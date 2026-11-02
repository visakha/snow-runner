# snow-runner — build notes (0.1.0)

Java 27, Spring Boot 4.1.2, Maven single module. The codebase implements specs
001–006 in `~/workspace/github/snow-runner/` (read-only reference; this
directory is a separate build tree and is never pushed from here).

## Build and run

```bash
mvn test          # compile + 28 tests (MockMvc + hand-written fakes, no Mockito)
mvn verify        # tests + Spotless check + Checkstyle check (both build-failing)
mvn package       # builds target/snow-runner-0.1.0.jar
java -jar target/snow-runner-0.1.0.jar
```

Formatting: `mvn spotless:apply` reformats to googleJavaFormat;
`mvn spotless:check` verifies. Checkstyle uses `google_checks.xml` and fails
the build on violation. If Spotless and Checkstyle ever disagree about import
order, Spotless owns import order (a carve-out for that one rule is acceptable;
dropping Checkstyle is not).

Requires Maven 3.9.x and JDK 27. In a normal environment with Maven Central
reachable, plain `mvn test` is all there is to it — no local repository setup.

## Configuration (placeholders only)

`src/main/resources/application.yml` contains dummy hosts, dummy accounts, and
empty credentials. Real values come from environment variables at deployment:

| Variable | Meaning |
|---|---|
| `SNOWRUNNER_CONTROL_URL` / `SNOWRUNNER_CONTROL_USER` / `SNOWRUNNER_CONTROL_PASSWORD` / `SNOWRUNNER_CONTROL_DRIVER` | Control store (RUN tables). Production home per 005 §7 is `MDM_ETL_RUNNER.CONTROL` — see open item below. |
| `SNOWRUNNER_SNOWFLAKE_URL` / `SNOWRUNNER_SNOWFLAKE_USER` / `SNOWRUNNER_SNOWFLAKE_PASSWORD` | ETL target when the catalog flag selects Snowflake. |
| `SNOWRUNNER_ORACLE_URL` / `SNOWRUNNER_ORACLE_USER` / `SNOWRUNNER_ORACLE_PASSWORD` | ETL target when the catalog flag selects Oracle. |

`snowrunner.catalog.location` points at `classpath:steps-catalog.yml`
(a copy of the reviewed phase YAML). `snowrunner.catalog.tree-version`
defaults to `v3` (see open item: tree target).

No secrets ever live in the catalog YAML or in this repository tree. The
catalog holds procedure names, order, phases, touches, and rerun safety only.

## The catalog is data, not code

`steps-catalog.yml` carries `targetDatabase` (snowflake|oracle), per-database
schemas, per-step `procedure` + `oracleProcedure`, `phases`, `touches`, and
`rerunSafety`. Java never hardcodes a procedure name, schema, or step list:
the catalog bean is loaded once at startup (fail-fast on unknown
targetDatabase, duplicate step ids, missing procedure, unknown phase, or bad
rerunSafety) and everything else reads from it. Switching databases is a
flag edit plus restart; a run never changes database halfway. Oracle
procedure values in the shipped catalog are `DUMMY_ORA_<STEP_ID>` placeholders
from the review sketch — running with `targetDatabase: oracle` before the
mirror_into_oracle procedures exist fails preflight, by design.

## Assumptions for every open 007 item

These are the defaults the code runs with today. Each one is still the user's
(or the scheduler team's) decision to confirm; the code does not pretend
otherwise.

- **Control-table database name** — assumed `MDM_ETL_RUNNER`, schema
  `CONTROL`, per 005 §7. NOT confirmed by the user. It is a plain
  configurable connection (`SNOWRUNNER_CONTROL_*`); creating the database is
  a real act that still needs his go-ahead.
- **Auth mechanism** — placeholder only. `CallerIdentityResolver` reads
  `X-Caller-Id` / `X-Caller-Role` headers (`scheduler`|`support`); control
  endpoints require `scheduler`, reads allow either, anything else is 401/403
  in the 006 error envelope. This is NOT production auth. The assumed
  production mechanism is Entra ID app roles (scheduler group = control plane,
  support group = read-only), which must replace the header resolver before
  any production use. Only `HeaderCallerIdentityResolver` is deleted in that
  swap; no controller changes.
- **Dry-run role** — scheduler-only, as written in 006. Support callers get
  403 on dry-run.
- **Idempotency-key format** — opaque to the app, supplied by the scheduler,
  scoped to (workflowId, key). The app never invents a key. The scheduler
  team still has to commit to a stable format (workflow + fire time was
  suggested); until then, any stable string works.
- **Tree target** — `v3` (the base spine-first tree). The TYPE-FIX line is a
  separate copy until adopted; flipping the catalog's `tree-version` value
  does not change which procedures run — the procedure names in the catalog
  do. The value is recorded on every run for traceability.
- **Scope guard** — ETL only. The runner triggers ETL driver procedures; it
  does not trigger Compare or pipeline runs in v0.1.
- **Failure policy** — fail-fast per workflow. A failed step stops the
  workflow; remaining steps are SKIPPED/FAIL_FAST; resume re-runs only
  non-SUCCEEDED, non-disabled steps. Per-workflow continue-on-failure is not
  implemented (the schema already accommodates it).
- **Scheduler identity** — unknown external scheduler. The control plane is
  whoever presents the scheduler role; which scheduler product and identity
  holds it is still open.

## Deliberate deviations and things to know

- **Synchronous execution.** v0.1 has no queue and no execution threads, so
  POST /runs executes the workflow in the request and 202 means "accepted and
  executed to a terminal or paused state". The scheduler's HTTP client needs a
  timeout longer than the longest step (the CRE loader was observed at
  ~15 minutes). QUEUED stays reserved for a future queuing version; this is a
  documented deviation from 006's "asynchronous" wording.
- **Positional two-argument CALLs.** Steps are called as
  `CALL <schema>.<procedure>(?, ?)` (Snowflake) or
  `{call <schema>.<procedure>(?, ?)}` (Oracle) with parameters bound in
  catalog order (`$RUN_TIMESTAMP`, `$SOURCE`, literals). Preflight checks
  procedure existence only, NOT signatures (003 open item 1) — a driver whose
  signature differs from (P_RUN_TS, P_SOURCE) fails at CALL time. The
  mirror_into_oracle procedures must therefore match those signatures.
- **No procedure return summary is captured.** 005's RUN_STEP has no column
  for it, so recording one would mean inventing storage. Query id and
  duration are captured; the summary is not.
- **ADR vs ADDR.** The catalog file spells the address dimension `ADR`;
  002 says `ADDR` for the same concept. The catalog file was treated as
  authoritative, so the `Dimension` enum carries `ADR`.
- **Step position comes from list order** (1-based); the catalog file carries
  no position field. Step parameters are an ordered list, not a map, because
  CALL arguments are positional and a map would lose file order; step
  parameters override defaults by name at the default's position.
- **`oracleProcedure` is required on every step**, even for Snowflake runs,
  so a future flag flip cannot silently break.
- **The `databases:` section of the catalog file is ignored by the loader.**
  Connection details come from `application.yml` / environment, never from
  the catalog.
- **TIMESTAMP, not TIMESTAMP_NTZ, in the Flyway migration.** TIMESTAMP is
  Snowflake's alias for TIMESTAMP_NTZ, so the same migration runs on H2
  (tests) and Snowflake (production). Statements stay unqualified; the schema
  is selected by the connection.
- **RUN gains TARGET_DATABASE** over 005's column list, so a run records
  which database flag it ran under.
- **Two connections, kept separate on purpose.** The control store (RUN
  tables, Flyway, repository) and the ETL target (CALLs, preflight) are
  different DataSources. The control `JdbcTemplate` is `@Primary` because
  Boot's `NamedParameterJdbcTemplate` auto-configuration picks the
  single-candidate template, and Spring Data builds the repository on it:
  without a primary control template, the only template in the context is
  the ETL one and the repository silently queries the ETL database. That
  exact failure was caught by the test suite during this build.
- **Run creation uses an explicit INSERT, not `save()`.** Spring Data JDBC
  treats an assigned String id as an existing row, so `save()` on a new run
  issues an UPDATE that matches nothing and silently persists nothing.
  Later saves (after each step, on resume, on cancel) are updates of a row
  that exists by then and keep using `save()`.
- **NUMBER columns are read via `Number`, never cast.** H2 and Snowflake
  both return NUMBER as BigDecimal; the row mappers convert with
  `intValue()`/`longValue()` instead of casting to Integer/Long.
- **Phase classes are delegates, not executors.** One class per
  (source, phase, dimension), bound by a code registry (step id → handler
  bean, constructor injection). Handlers hold no ETL logic and issue no
  CALLs; the CALL goes through `ProcedureCaller`. Naming a class in YAML
  would need reflection to instantiate, which the project forbids, so the
  binding lives in code. `EciReactivationLoader` declares dimensions
  {ADR, PHN, ALT_ID} (the reactivation drivers span those dimensions), so it
  also attaches to the ECI address/phone/alt-id steps as a hook; hooks are
  observability-only today, so this is harmless but recorded.
- **Preflight stays read-only; flush/postflight are no-ops today.**
  Temporary work-table drops live inside loaders at mapping start, not in a
  Java preflight. A flush writing C_S_* would be new ETL logic (the MDM
  batch reads V_MDM_*), so no flush procedure is invented.
- **Boot 4 adaptations** (the specs were written against Boot 3 idioms):
  Jackson 3 moved databind to `tools.jackson.databind` (annotations stayed
  `com.fasterxml.jackson.annotation`); health indicators moved to
  `org.springframework.boot.health.contributor` (artifact
  `spring-boot-health`); `@AutoConfigureMockMvc` moved to
  `org.springframework.boot.webmvc.test.autoconfigure` (starter
  `spring-boot-starter-webmvc-test`); Flyway auto-configuration moved out of
  `spring-boot-autoconfigure` into `spring-boot-starter-flyway`;
  `SnowflakeStatement` moved to `net.snowflake.client.api.statement` in
  Snowflake JDBC 4.x. No `flyway-database-h2` dependency: Flyway 12.4.0
  bundles H2 support in `flyway-core` (the separate artifact does not exist
  at this version).
- **DQM crash window (why-comment, also in code).** If the app dies after a
  DQM CALL commits but before the step row is persisted, reconciliation
  marks the step FAILED and resume re-executes it, which may double-append
  DQM rows. This is diagnosable via the QUERY_TAG in Snowflake
  QUERY_HISTORY; there is no automatic dedup in v0.1.
- **Test-only threading.** The pause test runs the trigger on a worker
  thread so the test thread can issue pause mid-step. The application itself
  stays single-threaded; only the test harness uses a second thread.

## What was NOT verified

- **No live Snowflake or Oracle run.** All 28 tests run against H2 with
  hand-written fakes. The callers' QUERY_TAG / client-info paths and the
  `SnowflakeStatement` unwrap for the query id have never been exercised
  against a real database.
- **No live Oracle procedure exists yet.** The Oracle caller, the
  `{call ...}` syntax, and the `oracleProcedure` names are unproven until
  the mirror_into_oracle procedures land and a first Oracle run happens.
- **Prod/Oracle copy paths, the scheduler integration, and Entra ID auth**
  are unexercised. The header auth placeholder must not reach production.
- **Performance and timeouts** are unmeasured beyond the observed ~15 minute
  CRE loader; the default 30-minute step timeout (45 for CRE) is a starting
  point, not a measured bound.

## Verification

Final green run, 2026-10-30 (offline Maven repository populated from Maven
Central via curl, because the sandbox blocks Java's TCP connections; see the
note below):

- `mvn -o test` — **BUILD SUCCESS, 28 tests, 0 failures**: AuthTest 4,
  RunControllerTest 14, WorkflowControllerTest 3, CatalogTest 4,
  ExecutorTest 2, CallerSelectionTest 1. No Mockito anywhere
  (`grep -ri mockito src/` returns nothing).
- `mvn -o spotless:apply` then `mvn -o verify` — **BUILD SUCCESS**: tests
  28/28, `spotless:check` (googleJavaFormat) clean, `checkstyle:check`
  (google_checks.xml, build-failing) clean, spring-boot repackage produced
  `target/snow-runner-0.1.0.jar` (contains `steps-catalog.yml` and the
  application class, verified with `unzip -l`).
- Three real bugs found by the suite and fixed in production code, not in
  tests: the repository silently queried the ETL database (fixed with an
  explicit `@Primary` control template); triggered runs were never persisted
  (Spring Data JDBC `save()` on an assigned id issues UPDATE, so creation
  now uses an explicit `insertRun`); NUMBER columns read as BigDecimal
  (row mappers now convert via `Number`).
- Build command: Maven 3.9.x + JDK 27. No Maven wrapper is shipped: offline
  generation of the wrapper could not satisfy maven-wrapper-plugin's
  transitive version set (commons-lang3 3.14.0 and friends), so it was
  skipped rather than faked; `mvn` on PATH is the documented command.

Offline-build note (environment, not project): this machine's sandbox
answers Java TCP connections with a policy refusal while curl through the
same egress proxy succeeds. The project itself needs no proxy settings and
none are shipped in it; on a normal machine `mvn test` / `mvn verify` work
online with no extra setup.
