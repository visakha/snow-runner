# 002 — Domain, dimension, spine, non-spine: how Java models them

> Companion to [001_SPEC.md](001_SPEC.md). Still spec, still no code.
> This document answers one question: **of everything we slice and dice
> — domain, dimension, spine, non-spine, source, run, side — what
> becomes an interface, what becomes a service, what becomes a
> repository, what becomes a named query, and what is deliberately not
> modeled in Java at all?**
>
> Two decisions from review are baked in here:
>
> - **Catalog: YAML-driven, declarative.** Not Snowflake-table-driven.
>   YAML is easier to work with, versioned, reviewable, and works
>   offline for dry-runs. (This decides 001_SPEC §13 question 3.)
> - **Data access: plain Spring Data JDBC.** No reflection, no invented
>   mini-ORM, no JPA-style magic. (Reaffirmed 2026-10-28.)

## 1. The one sentence that governs everything

**Java models the workflow, not the business.**

Snowflake owns members, addresses, phones, relationships, flags, and
views. Java owns runs, steps, catalogs, and history. Domain, dimension,
spine, and non-spine appear in Java **only as vocabulary** — for
selecting steps, filtering history, and naming things in logs and API
responses. Java never loads a member row, never mutates an address row,
never computes a flag, and never re-implements a spine predicate.

Why this boundary, in plain terms:

- **Single source of truth.** A spine predicate lives in exactly one
  place: the Snowflake loader/view that computes it. The moment Java
  holds a second copy of that predicate, the two copies will drift,
  and nobody will know which one is right.
- **No JPA-style object graph.** Modeling a Party that *contains*
  Addresses that *contain* Phones is exactly the aggregate-graph
  thinking Spring Data JDBC is chosen to avoid. The relationships
  between dimensions live in Snowflake foreign keys, not in a Java
  object tree.
- **Java adds no value at row level.** Java never reads a C_S_* row to
  make a decision. Every decision Java makes — run, skip, retry,
  resume, fail — is made from *step status*, never from row content.
  Row content is the Compare tool's job, not this application's.

If a future feature ever needs Java to read row content, that is a
scope change to argue about explicitly — not something to slide in
through a repository.

## 2. The slicing matrix: every axis, and where it lives

This is the full set of ways we slice the problem today, and for each
axis, what Java does with it:

| Slice axis | Values (from the estate) | Where the truth lives | What Java models | What Java never does |
|---|---|---|---|---|
| Source | TRS, ECI, FSS, WIC, TTF, CRE | Snowflake schemas + catalog | `Source` enum; selects workflows and schemas | Never infers source from a table-name string at runtime |
| Domain | MEMBER (in scope); PROVIDER (out of scope) | Estate scope decision | `Domain` enum with MEMBER only (see §3.1) | Never models Provider "just in case" |
| Dimension | PTY, RLE, ADDR, PHN, EADDR, ALT_ID, PLN, REL, TOA, NM, MNG_ATTR | Driver inventory (see note below) | `Dimension` enum; a step declares the dimensions it touches | Never models a dimension as an entity with rows |
| Spine vs non-spine | Spine (publish surface) vs non-spine (enrichment, diagnostics, audit) | Snowflake flags + `V_MDM_*` / `V_CMP_*` / `V_AUDIT_*` views | `SpineScope` enum for filtering/reporting only | Never computes, evaluates, or branches on a spine predicate |
| Flag reason | `LOW_SCORE:<score>`, `LOOKUP_NOT_FOUND:PHN_TP`, `EMPTY_ADDRESS`, `LOOKUP_MISSING:…`, etc. | Snowflake loaders/passes that write the reason | `FlagReason` as an opaque string label, for logs and filters | Never parses a reason string into control flow |
| Run | One run = one `P_RUN_TS` | Java (generated per run) | `RunTimestamp` value object | Never lets a step default its own timestamp |
| Side | `DATA_SOURCE` = IDQ vs SNOWFLAKE | Compare tables | **Not modeled in v0.1** — that axis belongs to Compare | Never filters a step or a run by side |
| Version | `VERSION_SEQ`, period timelines | Snowflake | **Not modeled** | Never sequences, versions, or orders rows |

Note on the dimension list: it is taken from the driver inventory
(Pty, Addr, Pty_Rle_Alt_Id, Pty_Rle_Phn_And_EAddr, Pty_Rle_Rel,
Pty_Rle_Pln, Pty_Rle_TOA, Pty_Nm, Pty_Mng_Attr, plus validation and
deactivation drivers). Whether EADDR stays a separate dimension or
folds into PHN, and whether validation/deactivation count as
dimensions or as step kinds, are open questions (§8). The list is
proposed vocabulary for review, not a claim about the estate.

## 3. What becomes what

### 3.1 Vocabulary: enums and value objects (no behavior, no setters)

Fixed, closed vocabularies become **enums**. Run-scoped identities
become **immutable value objects** (records in Java terms, validated
at construction, no setters, no nulls sneaking in):

- `Source` — TRS, ECI, FSS, WIC, TTF, CRE.
- `Domain` — MEMBER only. Provider is deliberately **not** an enum
  value: modeling an out-of-scope concept as a live value invites
  someone to write code against it. Scope lives in documentation
  until the day Provider actually enters scope, at which point adding
  one enum value is a one-line, reviewable change.
- `Dimension` — the list in §2. A step declares `Set<Dimension>
  touches` (a driver often touches several — the CRE care-clients
  driver touches PTY, RLE, ADDR, PHN, and ALT_ID in one CALL).
- `SpineScope` — SPINE, NON_SPINE, ALL. Used only to filter history
  and reports. It never selects a predicate and never gates a step.
- `WorkflowId` — one per workflow: TRS_ONGOING, TRS_TOA, ECI, FSS,
  WIC, TTF, CRE. A workflow belongs to exactly one source.
- `StepStatus` — QUEUED, RUNNING, SUCCEEDED, FAILED, SKIPPED.
  `RunStatus` — QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED.
- `RerunSafety` — SAFE (loader / EXC pass: guards make re-runs
  idempotent) vs APPEND (DQM pass: re-running duplicates rows).
  This is catalog metadata, declared per step, and it is what makes
  resume safe (§5).
- `RunId` — a UUID wrapper, not a bare string, so a run id can never
  be confused with a step id or a procedure name at a call site.
- `RunTimestamp` — an instant wrapper. One per run, generated by
  Java, passed explicitly to every step, reused verbatim on resume.
  Constructing a step CALL without a `RunTimestamp` should not
  compile into existence — it should be a required parameter, not an
  optional afterthought.
- `ProcedureName` — schema + procedure name as one validated value,
  not two loose strings concatenated at the call site. String
  concatenation is where SQL-injection-shaped mistakes are born, even
  against a trusted catalog.

Design sketch (shape only, not implementation):

```java
enum Source { TRS, ECI, FSS, WIC, TTF, CRE }
enum Dimension { PTY, RLE, ADDR, PHN, EADDR, ALT_ID, PLN, REL, TOA, NM, MNG_ATTR }
enum SpineScope { SPINE, NON_SPINE, ALL }
enum RerunSafety { SAFE, APPEND }

record RunTimestamp(Instant value) { /* validated: never null */ }
record ProcedureName(String schema, String name) { /* validated: never blank */ }
```

### 3.2 Interfaces: behavior boundaries (one real implementation + test doubles)

An interface earns its existence when there are **two** plausible
implementations — the real one and a test double — or when a boundary
must stay replaceable. By that test, Java gets a small set:

- **`StepCatalog`** — `workflows()`, `stepsFor(WorkflowId)`,
  `enabledStepsInOrder(WorkflowId)`. The v0.1 implementation reads
  YAML (§6). The test implementation is in-memory. The interface is
  what keeps the executor honest: it never reads a YAML file itself.
- **`ProcedureCaller`** — `call(StepDefinition, RunParameters) ->
  StepOutcome` (outcome carries status, Snowflake query id, the
  procedure's returned summary string, and duration). The real
  implementation uses `snowflake-jdbc` with bound parameters. The
  test implementation returns canned outcomes. Nothing else in the
  application touches JDBC for procedure calls.
- **`PreflightCheck`** — one interface, several small implementations
  (`ProcedureExistsCheck`, `SchemaReachableCheck`). Each returns a
  pass/fail with the missing object named. Preflight aggregates them;
  it does not contain their logic.
- **`ResumePolicy`** — `stepsToRun(Run)` — the rule that a resume
  executes only steps that are not SUCCEEDED, starting from the
  first FAILED step. Isolating this rule in one place is what makes
  it reviewable — and what makes the DQM-append guarantee (§5)
  something you can point at, rather than something you hope the
  executor remembers.
- **`Clock`** (or `RunTimestampSource`) — injected, so tests can fix
  time and production uses real time. A timestamp generated from a
  static call is untestable; this is the smallest interface with the
  largest testing payoff.

Deliberately **not** interfaces: the vocabulary enums (§3.1) — an
enum with one implementation is just an enum; wrapping it in an
interface adds indirection without a second implementation. And the
aggregates themselves (§3.4) — `Run` and `RunStep` are concrete;
their behavior (transition rules) lives on them, not behind an
interface nobody else implements.

### 3.3 Services: thin application services, no state, no SQL

Services orchestrate; they do not own data, SQL, or Snowflake
connections. Three of them, each stateless and constructor-injected:

- **`RunExecutor`** — the state machine. Start a run (create record,
  generate timestamp, preflight, loop steps fail-fast), resume a run
  (ask `ResumePolicy` which steps, reuse the timestamp), cancel a run
  (stop issuing CALLs, record where it stopped). It calls
  `ProcedureCaller` and persists through the repository. It contains
  no SQL, no YAML parsing, and no Snowflake driver code.
- **`PreflightService`** — runs the `PreflightCheck`s, aggregates
  failures, and produces the one plain-language failure ("procedures
  missing: X, Y") that stops a run before its first CALL.
- **`RunQueryService`** — the read side for REST and CLI status
  queries (find run, list steps, filter history). Separating reads
  from the executor keeps the write path small and the read path
  free to add filters (by source, dimension, status, spine scope)
  without touching execution logic.

What services are **not**: there is no `PartyService`, no
`AddressService`, no `SpineService`. A service per business dimension
would imply Java owns business behavior — it does not. Services are
per *workflow concern* (execute, preflight, query), never per
business dimension.

### 3.4 Repositories: Spring Data JDBC, control-plane aggregates only

Exactly **one repository**: `RunRepository`, a Spring Data JDBC
repository over the `Run` aggregate. `Run` owns its `RunStep`s
(`@MappedCollection` in Spring Data JDBC terms); steps are saved,
loaded, and modified only as part of their run.

This is the Spring Data JDBC aggregate rule applied strictly, and it
is doing real work here:

- **No `StepRepository`.** A step saved outside its run would let
  two writers disagree about a run's state. Steps are never
  addressable for writes on their own — only through the run, via
  the executor.
- **No repository per dimension, per source, or per C_S_* table.**
  A `C_S_TRS_PTY` repository would be Java claiming ownership of
  Snowflake's data plane (§1). There is none, and adding one later
  is a scope change, not a convenience.
- **No generic/base repository.** No shared "find all / save all"
  abstraction, no reflection-driven query derivation beyond what
  Spring Data JDBC itself provides for the aggregate. Named queries
  (§3.5) are explicit, per-purpose, and few.

### 3.5 Named queries: constants, few, and never against C_S_* tables

Queries live as **named constants** in one place per repository or
caller — never built by concatenation at a call site, never derived
by method-name magic where the intent is not obvious. The complete
v0.1 list:

**Control-plane reads (run history):**

- Find a run by id, with its steps, in order.
- List runs by source and status, newest first (the scheduler's
  "what failed last night" query).
- List steps of a run filtered by status and/or dimension touched
  (the "show me the ADDR steps that failed" query).
- Find the first non-SUCCEEDED step of a run (resume's starting
  point — though `ResumePolicy` owns the rule, the read itself is
  one named query).

**Snowflake metadata reads (preflight only):**

- Does procedure X exist in schema Y?
  (`INFORMATION_SCHEMA.PROCEDURES`, bound parameters.)
- Is schema Y reachable with the configured role?
  (A trivial read-only probe, not a CALL.)

**Named CALL templates (not queries, but the same discipline):**

- One template per step, from the catalog:
  `CALL <schema>.<procedure>(?, ?)` with `P_RUN_TS` and `P_SOURCE`
  bound as parameters. The schema and procedure name come from the
  validated `ProcedureName` value object, never from user input, and
  the parameter list is fixed per step in the catalog — Java never
  assembles a CALL string from request data.

**Explicitly not named queries, anywhere in this application:**

- Anything selecting from a `C_S_*` table or a `V_*` view. Row
  counts, PKEY sets, spine comparisons — that is the Compare tool.
  If snow-runner grows a row-reading query, it has grown a second
  Compare, and the two will disagree one day.
- Anything selecting DIAG/EXC/DQM rows. Diagnostics are Snowflake's
  audit surface, surfaced through its own views and monitors — not
  through this application's repositories.

### 3.6 Deliberately not modeled (the "no" list, for review)

| Tempting model | Why not |
|---|---|
| `Party`, `Address`, `Phone` entities | Business rows live in Snowflake; Java never reads them (§1) |
| Spine predicates in Java (`isSpine(row)`) | Would duplicate Snowflake truth; drift is a matter of time |
| Flag computation / DIAG code logic | Snowflake loaders and passes own it; Java treats reasons as opaque labels |
| `VERSION_SEQ` sequencing in Java | Snowflake timelines own it |
| `DATA_SOURCE` side filtering | Compare's axis, not this application's (§2) |
| MDM batch invocation | Another team's code, outside scope (001_SPEC §1) |
| A generic repository / mini-ORM | Rejected by standing rule: plain Spring Data JDBC, explicit repositories, named constants |
| Reflection-driven step discovery ("scan the schema for procedures") | The catalog is declarative YAML; discovery would reintroduce a second, implicit source of order |

## 4. Boundaries, stated as rules

1. **Control plane / data plane.** Java: runs, steps, catalog,
   history. Snowflake: rows, flags, views. Neither reaches into the
   other's plane except through the two sanctioned crossings:
   procedure CALLs (Java → Snowflake) and metadata preflight reads
   (Java → Snowflake, read-only).
2. **The catalog is the only order.** Snowflake runners are the
   reference the catalog is verified against in preflight — not a
   second source of order imported at runtime.
3. **Java never branches on Snowflake strings.** Flag reasons, DIAG
   codes, and procedure summary text are opaque labels for display
   and filtering. No `if (reason.startsWith(...))` in Java, ever.
   The day Java branches on a reason string is the day a Snowflake
   wording change becomes a Java bug.
4. **One aggregate, one repository.** Steps live and die inside
   their run. No back-door writes.
5. **One step, one workflow, one source.** No cross-source steps, no
   shared steps. A step that two workflows need is declared twice,
   once per workflow, so disabling it in one workflow never silently
   affects the other.
6. **One timestamp per run.** Generated by Java, passed explicitly,
   reused on resume. No step defaults, no `CURRENT_TIMESTAMP` inside
   a step's filter logic via Java's omission.
7. **Configuration is YAML, not a Snowflake table (§6).** No
   bootstrap chicken-and-egg, no second database to operate before
   the application can start.
8. **Errors propagate verbatim, with context added.** A Snowflake
   error reaches the operator with the failing step and query id
   attached. Java never translates an error code into a new meaning
   of its own.
9. **Adapters stay thin.** REST controllers and CLI commands
   translate input/output and nothing else. Every decision lives in
   `RunExecutor`, `ResumePolicy`, or the catalog — never in a
   controller.

## 5. Fluent sense: how the code should read

The user asked for Java "the way it is supposed to be used" — the
test is whether the important operations read as sentences, with no
boolean traps, no nulls, and no stringly-typed parameters. Design
sketches (shapes, not implementation):

```java
// Selecting work: reads like the sentence it is.
catalog.workflows()
       .forSource(Source.TRS)
       .steps()
       .enabled()
       .inOrder();

// Starting work: required things are required, nothing is a bare boolean.
RunRequest.forSource(Source.TRS)
          .workflow(WorkflowId.TRS_ONGOING)
          .dryRun(false)
          .submit();

// Asking what happened: narrow types at each step, so illegal
// states are hard to express.
run.steps()
   .withStatus(StepStatus.FAILED)
   .first();

run.steps()
   .touching(Dimension.ADDR)
   .withStatus(StepStatus.SUCCEEDED);
```

Fluency rules that follow from these sketches:

- **No boolean traps.** `run(source, true, false)` is unreadable and
  unreviewable. Modes are enums or dedicated methods (`dryRun()`,
  `resume(runId)`), never positional booleans.
- **No nulls across boundaries.** Optional things are `Optional`,
  required things are constructor parameters. A `RunRequest` without
  a source does not exist.
- **Narrow return types.** `enabled()` returns an enabled-steps
  view, not a list the caller must remember to filter. The type
  system carries the constraint so the reader doesn't have to.
- **Filters map 1:1 to enums in the API.** `GET
  /runs?source=TRS&dimension=ADDR&status=FAILED` — an invalid enum
  value is a 400, never silently ignored and never silently
  coerced. The API's vocabulary is the domain's vocabulary.
- **Dimension is a reporting slice, not an execution slice, in
  v0.1.** You can *ask* for the ADDR steps of a run. You cannot
  *run* only the ADDR steps of a workflow — mappings depend on each
  other, and a partial-dimension run would break those dependencies
  silently. If dimension-scoped execution is ever wanted, it is a
  separate design with its own dependency analysis, not a flag on
  the existing one.

## 6. YAML-driven declarative catalog (decision, with its trade-off)

The catalog is YAML, in this repo, versioned with the application.
Shape sketch (configuration, not code):

```yaml
workflows:
  - id: TRS_ONGOING
    source: TRS
    steps:
      - position: 10
        procedure:
          schema: TRS_ETL_WRK
          name: sp_snow_main_m_Tgt_C_S_TRS_Pty_OnGoing
        enabled: true
        rerunSafety: SAFE
        touches: [PTY]
        timeoutMinutes: 30
      - position: 20
        procedure:
          schema: TRS_ETL_WRK
          name: sp_snow_main_m_Tgt_C_S_TRS_Addr_1
        enabled: true
        rerunSafety: SAFE
        touches: [ADDR]
        timeoutMinutes: 30
```

What the YAML holds: **names, order, toggles, and metadata** —
procedure identity, position, enabled flag, rerun-safety class,
dimensions touched, timeout. What it never holds: SQL logic,
predicates, or transformations. Those stay in Snowflake, full stop.

Why YAML wins here, beyond "easier to work with":

- **Versioned and reviewable.** An order change is a pull-request
  diff, not a row update nobody reviewed.
- **No bootstrap problem.** A table-driven catalog needs a working
  Snowflake connection and a populated table before the application
  can even start — including for `--dry-run` and IDE runs. YAML
  starts anywhere.
- **Deploys with the application.** The catalog version recorded on
  a run (§4 of 001_SPEC) is simply the application's git commit.
  There is no second version to reconcile.

The honest trade-off, stated plainly: **toggles require a redeploy.**
An operator cannot flip a step off by updating a row; they edit YAML
and redeploy. That is the accepted cost of versioned configuration.
If operations later needs hot toggles without redeploy, that is a
real feature with its own design (audit trail for toggle changes,
who flipped what and when) — not a quiet drift back to a table.

## 7. Worked example: a TRS Ongoing run, end to end

1. The scheduler runs the CLI: `run --source TRS --workflow
   TRS_ONGOING`. The CLI builds a `RunRequest` (source and workflow
   required, nothing else positional) and hands it to the executor.
2. The executor creates a `Run` (QUEUED → RUNNING), generates one
   `RunTimestamp`, and runs preflight: every enabled step's
   procedure exists in its schema, schemas are reachable. A missing
   procedure stops the run here, naming the missing object — before
   a single row is touched.
3. Steps execute in catalog order. Each CALL binds `P_RUN_TS` and
   `P_SOURCE`; each outcome records status, Snowflake query id, the
   procedure's summary string, and duration, inside the run
   aggregate.
4. Step 7 fails. The run stops (fail-fast), the CLI exits non-zero
   with the run id, and the REST view shows exactly the failed step
   with the verbatim Snowflake error. Nothing is retried silently.
5. The operator fixes the Snowflake side and resumes. The resume
   reuses the original `RunTimestamp` and executes only steps that
   are not SUCCEEDED — including skipping a SUCCEEDED DQM step, so
   no duplicate DQM rows are created by recovery. The run completes;
   the history shows the original attempt and the resume as one run
   with two attempts, not two unrelated runs.
6. Later, an operator asks: "show me the ADDR steps of that run."
   That is a filter over the run's steps by `touches` — a reporting
   slice (§5), answered from the run aggregate, without touching
   Snowflake at all.

## 8. Open questions for review

1. **Dimension vocabulary:** is the §2 list exact? In particular:
   does EADDR stay separate or fold into PHN, and are
   validation/deactivation step kinds rather than dimensions?
2. **Step → dimension mapping:** is `Set<Dimension> touches` on a
   step the right shape, or should dimension be a reporting tag
   applied after the fact rather than catalog metadata?
3. **FlagReason representation:** free string (opaque label,
   recommended — reasons are Snowflake-defined and grow) or a
   closed enum of known reasons (would rot on the first new reason)?
4. **Provider in the enum:** keep `Domain` MEMBER-only
   (recommended — scope lives in docs), or include PROVIDER as an
   explicit out-of-scope value to make the boundary visible in code?
5. **Run-history home:** untouched by this document — still 001_SPEC
   §13 question 4 (Snowflake control tables vs application-side
   database).
6. **Dimension-scoped execution:** confirmed out of v0.1 (§5)?
   Dimension stays a reporting slice unless a dependency analysis
   justifies execution slicing later.

## 9. What changes in 001_SPEC because of this document

- §13 question 3 (catalog home) is **decided: YAML** (§6 above).
  The decision is also recorded in 001_SPEC's decisions box.
- §12's package shape is refined, not replaced: `domain` holds
  aggregates **and** vocabulary (enums, value objects); `catalog`
  holds the `StepCatalog` interface and its YAML adapter;
  `execution` holds `RunExecutor`, `ResumePolicy`, and preflight;
  `persistence` holds the single `RunRepository` and its named
  queries; `api` stays a thin adapter; there is no `cli` adapter (CLI
  command interface removed 2026-10-28 — process launch only).
- Nothing else in 001_SPEC changes. In particular, the non-goals,
  the fail-fast rule, the sequential-across-sources default, and the
  Spring Data JDBC constraints all stand exactly as written.

---

*Next: review §8 in conversation. The following document in sequence
will be `003_…`, whatever the next topic is — numbering continues
from here, for markdown and audio alike.*
