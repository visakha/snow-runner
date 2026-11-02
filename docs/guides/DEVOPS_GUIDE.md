# snow-runner — DevOps guide

Deploying the Java orchestrator in this repo, with the clean-ETL
constitution as its design record. The Oracle mirror of the ETL
tree is a separate delivery (shipped as a zip, not in this repo);
§3 below covers what the target ETL database still needs before
any real run, whichever side you point at.

## 1. What is in this repo

- Maven single module at the repo root (Java 27, Spring Boot
  4.1.2, Spring Data JDBC). `BUILD_NOTES.md` is the builder's
  record. `target/` (build output) is intentionally NOT committed.
- `001_SPEC.md` … `006_API_CONTRACT.md` — the spec the code was
  built against. `CODING_GUIDELINES.md` — the code rules.
- `docs/constitution/` — the clean-ETL constitution deck and
  guide (the philosophy this codebase implements).
- `docs/guides/` — this file and `USER_GUIDE.md`.
- The spec repo's earlier cross-check fixes are already in the
  bundled catalog: driver steps carry zero parameters, the Oracle
  schemas are the same names as the Snowflake side, and every
  `oracleProcedure` is the mirrored routine name. Do not copy the
  older review sketch's `defaults.parameters` or `DUMMY_ORA_*`
  values over it.

## 2. Prerequisites

- JDK 27 and Maven 3.9.x. No Maven wrapper is shipped (see §5).
- Oracle 19c or later for the mirror, or Snowflake for the original
  tree. The Java app talks to exactly one ETL database per deployment,
  chosen by the `targetDatabase` flag in `steps-catalog.yml`.
- A control database for run history (Flyway-managed, §4).
- For Oracle: schemas (users) for each source — `TRS_ETL_WRK`,
  `ECI_ETL_WRK`, `FSS_ETL_WRK`, `WIC_ETL_WRK`, `TTF_ETL_WRK`,
  `CRE_ETL_WRK` — plus a schema literally named `RDM` (helper44 reads
  `RDM.REF_PHN_AREA_CD`). Create them before installing anything;
  a missing `RDM` fails closed at install/call time, it does not
  degrade silently.

## 3. ETL database prerequisites (before any real run)

The app only calls drivers; the ETL database must already hold
everything the drivers need. Whether the target is Snowflake or
the Oracle mirror (delivered separately as a zip — its
`MIRROR_REPORT.md` is the translation record, including the five
items that can only close on a live database, headed by
`func().attribute` in SQL statements and the PIPELINED/AUTHID
clause order):

- The 40 drivers, 40 loaders, common passes, and 126 views,
  installed per source schema.
- For Oracle: schemas (users) for each source — `TRS_ETL_WRK`,
  `ECI_ETL_WRK`, `FSS_ETL_WRK`, `WIC_ETL_WRK`, `TTF_ETL_WRK`,
  `CRE_ETL_WRK` — plus a schema literally named `RDM` (helper44
  reads `RDM.REF_PHN_AREA_CD`). A missing `RDM` fails closed at
  install/call time, it does not degrade silently.
- The 7 reference objects with REAL definitions and data
  (`V_LOOKUP_CODE_VALUE`, `REF_JUNK_NAME_LIST`,
  `REF_MATCHCODE_DESC`, `RDM.REF_PHN_AREA_CD`,
  `EXC_LOOKUP_CODE_VALUE`, `LOOKUP_CODE_VALUE`,
  `REF_DYLGHT_SVNGS_TM`). Placeholder tables let the install
  succeed but return nothing from lookups — no real ETL run
  until these are real.
- A compile check with zero invalid objects, and a stub-driver
  CALL succeeding, before the scheduler is pointed at the
  database. **Nothing in the mirror has been compiled on a live
  Oracle database yet** — the translation is statically verified
  only.

Two translation facts that affect operations:

- All 120 converted mirror routines are FUNCTIONS (they all have
  RETURNS in Snowflake, so none can stay procedures). Java's
  Oracle caller uses the SQL `CALL` statement, which accepts
  functions with the return value discarded — standard Oracle
  CALL semantics, but itself unverified on a live database; if it
  ever raises an error, the fallback is the `{? = call ...}` form
  with a registered return parameter.
- Oracle DDL commits implicitly. A driver's ROLLBACK therefore
  cannot undo work-table DROP/CREATE steps the way Snowflake's
  does. Step transactions still protect loader+pass writes inside
  one driver; they do not roll back DDL.

## 4. Control tables (run history)

On startup the app runs Flyway migrations creating `RUN`,
`RUN_STEP`, and the `RUN_STEP_CURRENT` view. Default home is
database `MDM_ETL_RUNNER`, schema `CONTROL` — **this name still
needs the owner's confirmation** (open 007 item); change it in
configuration before the first production start if another name
is chosen. The control store is append-history: there is no
downgrade migration, so §9 rollback never touches it.

## 5. Build the jar

```bash
mvn verify
```

This runs tests, Spotless (`googleJavaFormat`) and Checkstyle
(`google_checks.xml`), all build-failing, and produces
`target/snow-runner-0.1.0.jar`. No wrapper is shipped: the build
environment used here could not generate one offline, so install
Maven 3.9 locally. On a normal machine with internet access,
plain `mvn verify` works with no extra setup and no proxy
settings (none are in the project).

## 6. Configure

Everything secret comes from the environment, never from YAML.
`src/main/resources/application.yml` ships placeholders only.

| Variable | Meaning |
|---|---|
| `SNOWRUNNER_CONTROL_URL` / `_USER` / `_PASSWORD` | Control database (run history). |
| `SNOWRUNNER_SNOWFLAKE_URL` / `_USER` / `_PASSWORD` | Snowflake ETL connection (key-pair auth recommended; see 001 §10). |
| `SNOWRUNNER_ORACLE_URL` / `_USER` / `_PASSWORD` | Oracle ETL connection. |
| `SNOWRUNNER_CATALOG_PATH` | Path to `steps-catalog.yml` (default: the bundled copy). |
| `SNOWRUNNER_TREE_VERSION` | Snowflake/Oracle tree version recorded on every run. |

The database flag lives in `steps-catalog.yml`:

```yaml
targetDatabase: snowflake   # or: oracle
```

`steps-catalog.yml` bundled in `src/main/resources` is the
corrected copy: driver steps carry zero parameters, the Oracle
schemas are the same names as the Snowflake side, and every
`oracleProcedure` is the mirrored routine name. Do not copy the
older review sketch's `defaults.parameters` or `DUMMY_ORA_*`
values over it.

## 7. Run

```bash
java -jar target/snow-runner-0.1.0.jar
```

The app is a standalone jar (no containers in v0.1). The external
scheduler owns the lifecycle: probe liveness, and if the app is
not alive, launch the jar and wait for health before triggering.
Singleton is by port bind — a second launch exits on port
conflict. The app never starts a run by itself.

**Authentication warning (read this before production):** the
shipped auth is a header-based placeholder (`X-Caller-Id`,
`X-Caller-Role: scheduler|support`) that exists so the two access
levels are enforceable in code. It is NOT production auth. Entra
ID app roles must replace it before any production deployment
(open 007 item). Control endpoints require the scheduler role
(wrong level → 403); read endpoints allow scheduler or support.

## 8. Switch databases

1. Stop the app. Never switch mid-run — a run never changes
   database halfway through, and the flag is read once at startup.
2. Edit `targetDatabase` in `steps-catalog.yml`.
3. Point the ETL connection at the other database (§6).
4. Restart. Preflight runs before the first CALL of every attempt
   and fails closed, naming any missing procedure — a run against
   an uninstalled mirror stops there, it does not half-execute.

## 9. Scheduler integration

- Trigger: `POST /api/v1/runs` with a stable, scheduler-provided
  idempotency key (required; scoped to workflow + key). A retry
  with the same key returns the existing run (200), never a
  duplicate.
- A 202 means accepted and executed to a terminal or paused
  state — v0.1 runs synchronously in the trigger request, so the
  scheduler's HTTP timeout must exceed the longest step (the CRE
  loader was observed at ~15 minutes; defaults are 30 minutes,
  45 for CRE).
- One active run per workflow: a second trigger while one is
  RUNNING or PAUSED returns 409 with the active run id.
- On app restart, runs left RUNNING are reconciled to FAILED
  ("application stopped mid-run") and are resumable by the normal
  resume call. Resume re-executes only non-SUCCEEDED,
  non-disabled steps.

## 10. Monitoring

- Snowflake: every CALL carries `QUERY_TAG`
  `snow-runner:<runId>:<stepId>`, joining app logs to Snowflake
  `QUERY_HISTORY`.
- Oracle: the caller sets client info to the same
  `snow-runner:<runId>:<stepId>` value via `DBMS_APPLICATION_INFO`.
- Logs carry the run id on every step event; metrics cover runs,
  step durations, failures, and in-flight counts. Actuator health
  is the liveness probe. Alert on: run failure, preflight
  failure, application launch failure, and the dead-man case (no
  successful run inside the expected window — windows and
  recipients are still open 004 items).

## 11. Rollback

Replace the jar and `steps-catalog.yml` with the previous
versions and restart. Runs already recorded stay recorded
(append-history); a run left RUNNING by the old jar reconciles
to FAILED on the new jar's startup and can be resumed. There is
no data rollback for ETL writes — drivers commit per step, and
DQM steps append (see the user guide: never blindly re-run an
APPEND step; resume is safe because it skips SUCCEEDED steps).

## 12. Known limits of this delivery

- Mirror: statically verified only; live Oracle compile + stub
  CALL + parity run still required (§3).
- Java: tested with mock data only (30/30 tests); no live
  Snowflake or Oracle run has been made from this codebase.
- `VARCHAR2(4000)` is the assumed width for unbounded Snowflake
  VARCHARs; values over 4000 chars would fail where Snowflake
  would not (mirror report §7).
- Header auth placeholder (§7) and all other 007 items remain
  open decisions, recorded in `BUILD_NOTES.md`.

## 13. The constitution, for the deployer

The code comments now cite the clean-ETL constitution
(`docs/constitution/clean-etl-constitution-guide.md`) at every
seam where it bites. The operational consequences:

- **Stateless runs, stateful bookkeeping.** The only state this
  app keeps is RUN / RUN_STEP rows in the control database. It
  keeps no ETL state, reads no `C_S_*` or `V_*` tables, and
  branches on no flag — so a redeploy or restart cannot corrupt
  a run's inputs. The control store is append-history (§4).
- **The flag is necessary, not sufficient.** `targetDatabase`
  selects caller, schemas, and procedure names. It does not
  translate anything: the Oracle side works only because the
  mirror tree exists, and the mirror is statically verified only
  (§3). Switching databases is edit-flag plus restart (§8).
- **Naming is the contract.** Step ids, procedure names, and
  class names all say source, dimension, and phase (spine loader
  versus non-spine enrich / exc / dqm, plus the valexc
  validation gate). If a future procedure breaks that pattern,
  the catalog — not the code — is where it is recorded.
- **The bookends are the one constitution rule not yet built.**
  Every run should start by dropping work tables and truncating
  `C_S_*` tables; neither this app nor the drivers do that
  today, so runs append. Do not schedule around an assumption
  of clean starts until the bookends land, and do not implement
  them ad hoc in a deployment script — the owner decision (a
  run-start step in the app versus driver changes) is still open,
  and a half-built bookend is worse than an honest gap. The
  user guide states the same gap for operators.
