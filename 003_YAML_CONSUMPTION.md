# 003 — How Java consumes the YAML catalog and turns it into executable workflows

> Companion to [001_SPEC.md](001_SPEC.md) (the orchestrator spec) and
> [002_DOMAIN_MODEL.md](002_DOMAIN_MODEL.md) (the modeling decisions).
> This document is the written form of the 003 audio explanation, plus
> everything established so far that the explanation depends on. Still
> spec, still no code.
>
> What this document assumes (all decided, all recorded in 001/002):
>
> - Driver-level steps, grouped by workflow (001, Option B).
> - Java generates one `P_RUN_TS` per run and passes it explicitly to
>   every step; resumes reuse the original timestamp (001).
> - Catalog is YAML-driven and declarative, not Snowflake-table-driven
>   (001 decisions box, 002 §6).
> - Plain Spring Data JDBC: no reflection, no invented mini-ORM, no
>   JPA-style magic (001, 002).
> - One `StepDefinition` class, one instance per catalog entry; a
>   step handle's `.run()` delegates to the executor — there is no
>   class per stored procedure (clarified in review).
> - One repository: the `Run` aggregate owns its `RunStep`s (002 §3.4).

## 1. The shape of the whole pipeline

There is exactly one path from file to execution, and every phase has
exactly one job:

```
steps-catalog.yml
  → [1. LOAD]      parse into typed records, validate, fail fast
  → [2. RESOLVE]   schemas + defaults → immutable StepDefinitions
  → [3. HOLD]      one immutable catalog bean, behind an interface
  → [4. RUN]       executor loop: bind → CALL → record → persist
  → [5. RECOVER]   resume / cancel / dry-run on the same path
```

No phase reaches past its neighbor. The loader never calls Snowflake.
The executor never reads the YAML file. The procedure caller never
knows about workflows. That separation is the entire design: each
boundary is a place where a mistake is caught early and where a test
can substitute a double.

## 2. Phase 1 — Load: YAML document into typed records

### What is parsed

The catalog file holds **names, order, toggles, and metadata** —
workflow ids, sources, step positions, procedure names, enabled
flags, rerun-safety classes, dimensions touched, timeouts, and
parameter bindings. It holds no SQL, no predicates, and no
transformations. Those stay in Snowflake, full stop.

Illustrative fragment (shape only — the full catalog is built from
the runner orders at implementation time, and the earlier TRS sample
remains a discussion sketch):

```yaml
catalogVersion: 1

schemas:
  TRS: TRS_ETL_WRK
  CRE: CRE_ETL_WRK

defaults:
  timeoutMinutes: 30
  parameters:
    - name: P_RUN_TS
      value: $RUN_TIMESTAMP
    - name: P_SOURCE
      value: $SOURCE

workflows:
  - id: TRS_ONGOING
    source: TRS
    steps:
      - position: 10
        id: TRS_PTY_ONGOING
        procedure: sp_snow_main_m_Tgt_C_S_TRS_Pty_OnGoing
        enabled: true
        rerunSafety: SAFE
        touches: [PTY]
```

### How it is parsed

- **Parser:** SnakeYAML under the hood, through Jackson's YAML
  support (the standard Spring Boot YAML stack). The file is parsed
  into **typed records** — a catalog document holding the schemas
  map, the defaults, and the list of workflows, each workflow
  holding its steps. It is **never** parsed into loose maps
  (`Map<String, Object>`) that later code must interpret by string
  keys. Loose maps are where typos become runtime surprises; typed
  records turn them into load errors.
- **Not Spring's `application.yml` binding.** The catalog is a
  domain document, not application configuration, so it is loaded by
  a dedicated catalog-loader bean from its own file — not by
  `@ConfigurationProperties` over the application's properties.
  That keeps the catalog versionable as a document, loadable in
  tests from an in-memory equivalent, and independent of the
  application's own config (connection settings, profiles, ports),
  which live elsewhere and never in this file. Secrets never appear
  in this file at all.

### Validation at load: fail fast, fail completely

A catalog problem is a **startup failure**, never a runtime
surprise halfway through a run. The loader rejects the whole
document — no partial catalog ever executes — on any of:

- Malformed YAML (parse error, with file and position reported).
- A missing required field: workflow id, source, step position,
  step id, or procedure name.
- An unknown enum value: a source, dimension, or rerun-safety
  class the vocabulary does not contain (a misspelled `TRSS` or
  `SAFEE` fails here, not at 2 a.m. during a run).
- A duplicate step id within a workflow, or a duplicate position
  within a workflow (order must be total and unambiguous).
- A duplicate workflow id.
- A blank procedure name, or a step whose source has no entry in
  the `schemas` map.
- An unknown parameter placeholder (anything that is not
  `$RUN_TIMESTAMP`, `$SOURCE`, or an explicitly declared literal —
  see §3).
- A non-positive timeout, or a position that is not a positive
  integer.

Every rejection names the file, the workflow, and the step. "Catalog
invalid" without a location is not an acceptable error message.

## 3. Phase 2 — Resolve: entries into immutable step definitions

Resolution still happens at startup, before any run exists. It turns
each step entry into an immutable `StepDefinition`:

- **Procedure identity.** The schema comes from the `schemas` map
  for the step's source, combined with the procedure name into one
  validated `ProcedureName` value (schema + name). The schema is
  never concatenated into a CALL string at the call site, and the
  procedure name never comes from user input — only from the
  catalog.
- **Defaults merge.** A step that does not specify a timeout gets
  the default (30 minutes in the sample); a step that specifies
  one (the CRE step's 45, reflecting an observed ~15-minute loader
  run with headroom) keeps its own. Step values always win over
  defaults; defaults never silently override a step.
- **Parameters stay as bindings, not values.** A parameter entry is
  a pair: a procedure-parameter name and a *binding*. Bindings are
  one of:
  - `$RUN_TIMESTAMP` — resolves, per run, to that run's single
    `RunTimestamp` (generated by Java at run start).
  - `$SOURCE` — resolves, per run, to the workflow's source.
  - A literal fixed value — for the rare step that genuinely
    needs one, declared explicitly in the catalog. Literals are
    the exception and must read as such in review.
  
  At load time, Java only checks that every binding name is known.
  Nothing is resolved to a real timestamp yet, because at startup
  there is no run. Resolving early would be a bug: it would bake
  one run's timestamp into every future run.
- **Order materializes.** Steps within a workflow are sorted by
  position once, at load, into an immutable ordered list. The
  executor never sorts at run time and never has to wonder whether
  the list is in order — it was fixed at load and cannot change
  afterwards.
- **Immutability.** The finished `StepDefinition`s, workflows, and
  catalog are immutable. Nothing at run time mutates the catalog:
  no step is added, removed, reordered, or toggled mid-run. A
  catalog change is a file change plus a restart/redeploy — which
  is the documented trade-off of YAML-driven configuration (002
  §6), and it is what makes "which catalog did this run use?"
  answerable from the application's version alone.

## 4. Phase 3 — Hold: one catalog bean behind an interface

The finished catalog is held as a **single Spring bean** for the
life of the application, exposed through the `StepCatalog`
interface (`workflows()`, `stepsFor(WorkflowId)`,
`enabledStepsInOrder(WorkflowId)`). The executor never reads the
YAML file itself; it asks the interface.

This boundary pays for itself three ways:

- **Testability.** In tests, the executor is handed an in-memory
  catalog with three steps and a fake procedure caller. The whole
  state machine — start, fail-fast, resume, cancel — is tested
  without Snowflake and without a file.
- **Single ownership.** There is exactly one place where "what
  runs, in what order" is answered. Nothing else in the
  application may hold a second copy of an order.
- **Reload discipline.** The catalog is loaded once at startup.
  There is no hot-reload in v0.1: a changed file takes effect on
  restart/redeploy, and the run record's application version tells
  you which catalog a run used. Hot-reload without versioning
  would make run history unreproducible, so it is out of scope
  until someone designs it properly.

## 5. Phase 4 — Run: the executor loop

A run is where the catalog becomes execution. Step by step:

1. **Trigger.** The scheduler invokes the CLI (or REST) with a
   source and a workflow. The adapter builds a `RunRequest`
   (source and workflow required, nothing positional, no boolean
   traps) and hands it to the executor.
2. **Run record created.** The executor creates a `Run` aggregate
   (status QUEUED → RUNNING) and generates **one `RunTimestamp`**
   for the whole run. This is the only timestamp the run will ever
   use.
3. **Preflight.** Before the first CALL, the executor verifies
   that every enabled step's procedure exists in its schema and
   that the schemas are reachable (read-only metadata checks).
   A missing procedure fails the run here, naming the missing
   objects — before a single row is touched. Missing objects are
   flagged, never invented, never worked around.
4. **The loop.** For each enabled step, in position order:
   - The step is recorded as RUNNING inside the run aggregate.
   - Parameter bindings resolve for *this* run: `$RUN_TIMESTAMP`
     becomes the run's timestamp, `$SOURCE` becomes the
     workflow's source, literals pass through unchanged.
   - The resolved call goes to the `ProcedureCaller`, which
     issues exactly one bound CALL —
     `CALL <schema>.<procedure>(?, ?)` — with parameters bound,
     never concatenated. No dynamic SQL, no string-built CALL.
   - The caller captures the outcome: status, the Snowflake
     **query id** (for `QUERY_HISTORY` correlation), the
     procedure's returned summary string, and the duration.
   - The executor marks the step SUCCEEDED or FAILED and
     **persists the run aggregate immediately, after every step**
     — not just at the end of the run.
5. **Fail-fast.** On a FAILED step, the loop stops. The run is
   marked FAILED, the CLI exits non-zero with the run id, and the
   REST view shows the failed step with the verbatim Snowflake
   error. Nothing is retried silently: a CALL that may have
   partially written must never be auto-retried — the operator
   decides, via resume or re-run, with the facts in front of them.
6. **Success.** The run ends SUCCEEDED only when every enabled
   step succeeded. Disabled steps are recorded as SKIPPED, never
   as success.

### Why persist after every step

If the application dies mid-run — crash, restart, deployment —
the record shows exactly which steps succeeded. Without per-step
persistence, a crash would leave the run's state unknown, and the
operator would have to guess which steps ran. With it, recovery is
mechanical (§6), not forensic.

## 6. Phase 5 — Recover: resume, cancel, dry-run on the same path

- **Resume.** Loading the run and asking `ResumePolicy` which
  steps to run: only steps that are not SUCCEEDED, starting from
  the first FAILED step, reusing the **original run's timestamp**.
  A resume is a continuation of the same run, not a new run — a
  fresh timestamp would strand already-loaded rows outside the
  resumed steps' run filters. This rule is also what makes the
  DQM-append limitation safe: a SUCCEEDED DQM step is never
  re-executed by a resume, so recovery cannot create duplicate
  DQM rows. A resume that re-ran SUCCEEDED steps would be a bug,
  not a feature.
- **Re-run.** A brand-new run (new id, fresh timestamp) over the
  same workflow, relying on the estate's mapping-level restart
  semantics: work tables dropped at mapping start, insert-only
  guards preventing double-inserts. A re-run does not undo what an
  earlier run published — the operator must understand that before
  re-running, and the CLI/REST response says so when a re-run
  targets a source with a recent SUCCEEDED run.
- **Cancel.** Stops the run from issuing further CALLs and marks
  it CANCELLED with the last completed step recorded. It does not
  kill an in-flight CALL and does not roll anything back — the
  estate has no rollback, and Java must not pretend otherwise. A
  cancelled run resumes like a failed one.
- **Dry-run.** The same pipeline with the caller removed: load,
  resolve, print the ordered steps with their resolved procedure
  names and parameter bindings, and stop. Same code path, same
  validation, zero CALLs, no run record (or a DRY_RUN record —
  001 §7 leaves that detail to review). This is the operator's
  "show me what you would do" command and the first thing
  demonstrated in the IDE.

## 7. Failure table: what happens when each thing goes wrong

| Failure | When it is caught | What happens |
|---|---|---|
| Malformed YAML | Load (startup) | Application fails to start; error names file and position |
| Missing field / unknown enum / duplicate id or position / unknown placeholder | Load (startup) | Application fails to start; error names workflow and step |
| Step's source has no schema mapping | Load (startup) | Application fails to start; error names the source |
| Procedure does not exist in its schema | Preflight (run start) | Run fails before any CALL; missing objects named |
| Schema unreachable | Preflight (run start) | Run fails before any CALL |
| Step CALL fails | Loop (run) | Loop stops; run FAILED; step recorded with verbatim error + query id; CLI exits non-zero |
| Application crashes mid-run | After restart | Run record shows last persisted step; resume continues from the first non-SUCCEEDED step |
| Operator resumes a run | Resume | Original timestamp reused; SUCCEEDED steps skipped (DQM-safe) |
| Operator re-runs a workflow | New run | Fresh timestamp; mapping-level restart semantics apply; earlier publication is not undone |
| Catalog file changed mid-run | Never observed | Catalog is immutable after load; changes take effect on restart/redeploy only |

## 8. What Java never does while consuming YAML

- **No reflection.** Step definitions are not discovered by
  scanning classes, annotations, or the Snowflake schema. The
  catalog declares them; the loader reads declarations.
- **No dynamic SQL.** No CALL string is ever assembled from
  request data, catalog text, or string concatenation at a call
  site. Procedure identity comes from a validated value object;
  parameters are bound.
- **No runtime discovery.** Java never asks Snowflake "what
  procedures exist?" to build the order. It asks only "does this
  declared procedure exist?" to verify it. Discovery would be a
  second, implicit source of order — the exact thing the catalog
  exists to prevent.
- **No silent coercion.** An unknown enum value, an unknown
  placeholder, or a missing field is an error, not a default.
  Defaults exist only where the catalog explicitly declares them
  (timeouts, parameter bindings) — never as a fallback for a typo.
- **No secrets in YAML.** Connection settings, keys, and
  credentials live in environment profiles and the secret store,
  never in the catalog file. A catalog file that can be committed
  to git must contain nothing that cannot be committed to git.

## 9. Open items carried from this document

1. **Driver signature verification.** Preflight as specified
   checks procedure *existence*. Whether it should also verify
   parameter names/arity against the catalog's bindings — and how,
   without parsing procedure bodies — is a catalog-test item for
   implementation. Until it exists, binding correctness is a
   catalog-review responsibility, and a signature mismatch will
   surface as a step CALL failure, not as a preflight failure.
   That gap is stated here so it is not discovered later.
2. **Catalog reload.** v0.1 loads once at startup (§4). If hot
   reload is ever wanted, it needs versioning per run to keep
   history reproducible — a design of its own, not a flag.
3. **Dry-run record.** Whether a dry-run creates a DRY_RUN run
   record or no record at all is still open (001 §7).
4. **Full catalog content.** The 40-step catalog itself is built
   from the runner orders at implementation time. The TRS sample
   shown in discussion remains a sketch; the authoritative catalog
   is the one that passes preflight against the installed
   procedures.

---

*Next in sequence: `004_…`, whatever the next topic is. Numbering
continues from here, for markdown and audio alike.*
