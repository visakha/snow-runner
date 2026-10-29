# 004 — Touch points and observability on Azure

> Companion to [001_SPEC.md](001_SPEC.md) (the orchestrator spec),
> [002_DOMAIN_MODEL.md](002_DOMAIN_MODEL.md) (modeling), and
> [003_YAML_CONSUMPTION.md](003_YAML_CONSUMPTION.md) (catalog
> execution). Still spec, still no code.
>
> Decisions recorded here (2026-10-28):
>
> - **Keep it simple.** No JaCoCo coverage enforcement, no Spring
>   Modulith, no ArchUnit/Taikai rule tests. This is a simple
>   project; that harness would be over-engineering. (This
>   supersedes the harness adoption previously noted in the README,
>   which has been corrected.)
> - **Touch points:** the application is reached and observed
>   through several surfaces — CLI for the scheduler, REST for
>   operations, and Azure-native observability for runtime view.
>   No single surface is the whole story.
> - **Deployment target: Azure.** Observability follows Azure best
>   practices for a typical Java application (and the same
>   practices Azure recommends for Python and .NET applications:
>   the platform services are the same; only the agent differs).

## 1. The touch points

| Touch point | Who / what uses it | What you get |
|---|---|---|
| CLI (`run`, `resume`, `status`, `runs`) | The external scheduler; an operator at a terminal | Trigger, resume, and status with exit codes; one result line per run, always carrying the run id |
| REST API (`/api/v1/runs…`) | Operators, dashboards, and any future automation | Start runs, read run and step status, resume, cancel, filter history |
| Actuator health (`/actuator/health`) | Azure health probes, load balancer, on-call | Liveness (is the app up?) separated from readiness (can it reach Snowflake?) — see §2 |
| Actuator metrics (`/actuator/metrics`, Prometheus exposition) | Azure Monitor | Counters, gauges, and durations the platform scrapes — the raw material for dashboards and alerts |
| Structured logs (stdout, JSON) | On-call engineers, Log Analytics queries | One event per run/step transition, all carrying the run id — the investigation trail |
| Run history (REST + control tables) | Operators reviewing yesterday's runs | The durable record: what ran, in what order, with what outcome, per step |
| Azure portal: Application Insights + Log Analytics + Workbooks | Anyone watching runtime | Live metrics, traces, log queries, and dashboards — the runtime view |
| Alerts (Action Groups) | On-call (email / webhook) | A tap on the shoulder when something needs a human, not a dashboard to stare at |

Nothing here is optional decoration. The CLI is how work starts,
REST is how humans inspect it, and the Azure surfaces are how
anyone knows — during runtime, not after the fact — whether the
system is healthy.

## 2. Observability principles for this application

1. **The run id is the correlation id, everywhere.** Every log
   line, every metric label, every trace, and every Snowflake
   query tag for a run carries the same run id. If you cannot go
   from a failed step to its logs with one filter, the
   observability has failed, no matter how pretty the dashboard
   is.
2. **One log event per step transition.** Run started, preflight
   passed/failed, step started, step succeeded, step failed, run
   ended, resume, cancel, dry-run. No chatty per-row logging (Java
   never sees rows anyway), no missing transitions. A run's story
   must be reconstructible from its log events alone.
3. **Metrics for alerting, logs for investigating.** Alerts fire
   on metric/status conditions (a run failed, no success in the
   expected window), never on substring searches over log text.
   Logs are what you open *after* the alert, filtered by run id.
4. **Health is not readiness.** Liveness says the process is up.
   Readiness says it can reach Snowflake with the configured role.
   Snowflake being down must make readiness fail — it must not
   make the platform kill and restart a healthy application in a
   loop.
5. **Join application telemetry to Snowflake telemetry.** Every
   CALL sets a Snowflake query tag carrying run id and step id
   (see §5). That is the bridge between "the app says step 7
   failed" and "Snowflake's query history says what that CALL
   actually did."
6. **No secrets in telemetry.** No credentials, connection strings,
   private-key material, or tokens in logs, metric labels, traces,
   or error messages. Ever. A log line that leaks a secret is a
   security incident, not a debugging aid.
7. **The platform collects; the app only emits.** The application
   writes structured logs to stdout and exposes Actuator
   endpoints. Azure Monitor collects, stores, and retains them.
   There is no self-managed log pipeline, no sidecar agent to
   operate, and no separate observability database to run. That
   is the simplicity decision applied to operations.

## 3. Logging

**Format:** structured JSON, one event per line, written to
stdout. The platform (App Service, Container Apps, or AKS — see
§6) collects stdout into Log Analytics automatically. No log files
on disk, no custom appenders to maintain.

**Fields on every event** (in addition to timestamp and level):

- `runId` — the run's id (also the trace/operation id, §5).
- `workflow` — e.g. TRS_ONGOING.
- `stepId` — e.g. TRS_PTY_ONGOING (absent on run-level events).
- `procedure` — schema + procedure name (absent on run-level
  events).
- `status` — QUEUED / RUNNING / SUCCEEDED / FAILED / SKIPPED /
  CANCELLED, as applicable.
- `queryId` — the Snowflake query id, on step outcomes.
- `durationMs` — on step and run completions.
- `error` — the verbatim Snowflake error, on failures only.
- `source` — TRS / ECI / FSS / WIC / TTF / CRE.
- `trigger` — CLI or REST, and the scheduler job id when supplied
  (for idempotency tracing).

**Events emitted** (the complete list for v0.1):

- Run requested (trigger, source, workflow, idempotency key
  presence — never the key's secret, if any).
- Run started (run id, timestamp used as `P_RUN_TS`, catalog
  version, application version, Snowflake tree version).
- Preflight passed / preflight failed (missing objects named).
- Step started / step succeeded / step failed (with query id,
  duration, and summary on success; verbatim error on failure).
- Run succeeded / run failed / run cancelled (with totals:
  steps succeeded / failed / skipped).
- Resume started (original run id, steps to run).
- Dry-run listed (ordered steps with resolved procedure names —
  no CALLs made).

**Levels:** INFO for the events above; WARN for recoverable
oddities (a retryable transient on a metadata read, a step
duration far above its timeout's shadow); ERROR only for run/step
failures and preflight failures. A failed run must be findable by
`level == ERROR`, not by grepping message text.

**Retention:** set in Log Analytics (a workspace setting, not
application code). The retention period itself is an open question
(§8) — it is a cost decision, not a code decision.

Example (shape only):

```json
{"timestamp": "2026-10-29T02:00:00Z", "level": "INFO",
 "runId": "9f2c…", "workflow": "TRS_ONGOING", "source": "TRS",
 "event": "STEP_SUCCEEDED", "stepId": "TRS_PTY_ONGOING",
 "procedure": "TRS_ETL_WRK.sp_snow_main_m_Tgt_C_S_TRS_Pty_OnGoing",
 "queryId": "01b…", "durationMs": 182000}
```

## 4. Metrics

Exposed via Spring Boot Actuator + Micrometer; collected by
Azure Monitor (Application Insights agent auto-collection and/or
Azure Monitor managed Prometheus scraping the Prometheus
endpoint — the deployment picks one collection path, §6; the
application exposes both the same way regardless).

| Metric | Type | Labels | Why it exists |
|---|---|---|---|
| `snowrunner_runs_total` | Counter | source, workflow, outcome | Success rate per source; the headline number |
| `snowrunner_run_duration_seconds` | Histogram | source, workflow | How long runs take; trend and anomaly detection |
| `snowrunner_step_duration_seconds` | Histogram | workflow, stepId | Which mapping is slow; the per-step view |
| `snowrunner_steps_failed_total` | Counter | workflow, stepId | Which mapping fails, how often |
| `snowrunner_runs_in_flight` | Gauge | — | Is anything running right now? |
| `snowrunner_scheduler_triggers_total` | Counter | trigger (cli/rest), outcome | Is the scheduler actually firing? (Absence of triggers is itself a signal) |
| `snowrunner_preflight_failures_total` | Counter | reason class | Missing-procedure / unreachable-schema trends |

Rules for metrics, stated plainly:

- Labels are low-cardinality vocabulary (source, workflow, step
  id, outcome). Never put a run id, a query id, or an error
  message in a label — that is a cardinality explosion, and it is
  what logs are for.
- Every metric the alerts need (§7) must exist before the first
  production run. An alert written against a metric that does not
  exist yet is not an alert.
- The application does not invent its own metrics pipeline.
  Micrometer + Actuator is the standard Spring Boot path, and
  Azure Monitor is the standard collection path. No custom
  metrics SDK code, no hand-rolled exporters.

## 5. Tracing: from scheduler to Snowflake CALL

- **Inbound:** when the scheduler triggers via REST, it passes a
  W3C `traceparent` header when it can; the application honors
  it, so a scheduler-side trace continues into the run. When the
  scheduler triggers via CLI (a job run), there is no inbound
  trace — the run id becomes the trace/operation id.
- **Within the application:** the run id is the operation id for
  every span and log event in that run. There is exactly one
  trace per run, not one per step — steps are events inside the
  run's trace, so the whole run renders as one timeline.
- **Into Snowflake:** every CALL sets the Snowflake session query
  tag to `snow-runner:<runId>:<stepId>` (set per CALL, not per
  session, so a reused connection never carries a stale tag).
  Snowflake's `QUERY_HISTORY` then joins to application telemetry
  two ways: by query id (captured on the step outcome) and by
  query tag (searchable without knowing the query id). This is
  the single most valuable tracing decision in this document,
  because it is the only one that crosses the Java/Snowflake
  boundary.
- **Outbound:** no other outbound calls exist in v0.1 (no MDM
  batch call, no Compare call), so there is nothing else to trace.
  When a future version adds one, it inherits the same run id as
  its operation id — it does not invent a new correlation scheme.

## 6. Azure deployment shapes

The application is one artifact (one jar, one container image)
with two execution shapes. Which Azure service hosts each shape is
a deployment decision (§8), but the shapes themselves are fixed:

| Shape | What runs | Scheduler relationship |
|---|---|---|
| Service (always on) | REST API + Actuator + run history reads; also accepts REST run triggers | Scheduler calls `POST /runs` |
| Job (runs to completion) | CLI: one run, then the process exits with the run's exit code | Scheduler triggers a job run (or the scheduler's cron drives it) |

Candidate hosts, honestly compared:

| Host | Service shape | Job shape | Notes |
|---|---|---|---|
| **Azure Container Apps (RECOMMENDED)** | Container App (scale to zero off, always on when needed) | Container Apps Job, including cron-scheduled jobs | One image for both shapes; environment + Key Vault references built in; managed identity built in. The job shape is a first-class citizen here, which matters because CLI runs are real work, not an afterthought. |
| Azure App Service | Web App (jar) | Not a natural fit — jobs need WebJobs or an external trigger calling REST | Fine if the team already standardizes on App Service **and** the scheduler uses REST only. The CLI shape then has no clean home, which is a real cost. |
| Azure Kubernetes Service | Deployment + CronJob | CronJob / Job | Fully capable, but the heaviest operational footprint for a simple orchestrator. Only if the team already runs AKS and wants this workload there. |

**Recommendation: Azure Container Apps**, with the external
scheduler either calling REST on the service or triggering the
job — whichever the scheduler already does best. The spec does
not force the scheduler to change shape; it meets it at either
touch point.

**Configuration and secrets (Azure best practice, all hosts):**

- Non-secret configuration via environment (App Settings /
  Container Apps environment variables): Snowflake account,
  warehouse, role, schema mapping, catalog path.
- Secrets via **Azure Key Vault**, referenced (never copied) by
  the application: the Snowflake private key for key-pair auth,
  and any password/token if a fallback auth path is ever used.
  The application authenticates to Key Vault with a **managed
  identity** — no stored credential for the credential store.
- Nothing secret lives in the repo, in the YAML catalog, in
  environment files committed to git, or in container image
  layers. A secret that can be read from the repo is not a
  secret.

**Collection path (Azure best practice):**

- **Application Insights Java agent** (attached with `-javaagent`,
  no code changes) for auto-instrumentation: incoming requests,
  dependencies, exceptions, and Live Metrics. This is the
  recommended primary path for a Java application on Azure, and
  it is the same agent story Azure recommends across Java
  workloads.
- **Actuator + Micrometer** for application metrics and health,
  as §4 describes. The agent and Actuator complement each other:
  the agent sees the process, Actuator sees the application's own
  numbers.
- **Log Analytics workspace** as the single store for logs,
  metrics, and traces. Application Insights data lives in (or is
  queryable through) that workspace, so one KQL query can span
  application logs and platform telemetry.

## 7. Alerts

Alerts fire on metric/status conditions, through Action Groups
(email, webhook, or whatever the team's on-call channel is).
Each alert names the run id and links to the failure drill-in
(§8). The v0.1 alert set:

| Alert | Condition | Why it matters |
|---|---|---|
| Run failed | Any run ends FAILED | The obvious one — a workflow did not complete |
| Step failure spike | Steps failed for one step id above its normal rate | A mapping that started failing repeatedly is a different problem from a one-off failure |
| No successful run in window | No SUCCEEDED run for a source within its expected schedule window | The dead-man switch: a scheduler that stopped firing, or a run that never started, produces no failure — silence is the failure mode this alert exists for |
| Preflight failure | A run fails preflight | Something changed in Snowflake (a missing procedure) before any work ran |
| Application unhealthy | Readiness or liveness failing | The service itself is down, not just a run |
| Step duration anomaly | A step's duration far above its historical p95 | A mapping that suddenly takes 3× as long is degrading, even if it still succeeds |

The expected schedule window per source (for the dead-man alert)
is operator-provided configuration — the application cannot know
a source's cadence by itself. That value is set at deployment,
per source, alongside the schedule.

## 8. Dashboards and views (what "a view on runtime" means)

Built as an Azure Workbook (or equivalent) over Log Analytics /
Application Insights — no custom dashboard product, no separate
frontend to maintain. Three views, each answering one question:

1. **Run overview.** Are runs succeeding, and how long do they
   take? Success rate and duration by source and workflow over
   time; in-flight runs; last successful run per source (with its
   age — the dead-man signal, visible at a glance).
2. **Failure drill-in.** What went wrong with this run? Filter by
   run id → the run's step table (status, duration, query id,
   error) → the step's log events → Snowflake `QUERY_HISTORY`
   for the same query id or query tag. One filter, end to end.
3. **Scheduler health.** Is the scheduler firing? Triggers over
   time by trigger type, runs started vs runs requested, and any
   trigger that produced no run (a scheduler-side failure visible
   from the application side).

Example investigations, in KQL shape (illustrative, not code):

```kql
// All events for one run, in order:
AppTraces
| where customDimensions.runId == "<run-id>"
| order by timestamp asc

// Failed steps in the last day, by mapping:
AppTraces
| where timestamp > ago(1d) and customDimensions.event == "STEP_FAILED"
| summarize failures = count() by tostring(customDimensions.stepId)
| order by failures desc
```

## 9. Simplicity guardrails (what is deliberately NOT here)

- No JaCoCo, no Spring Modulith, no ArchUnit/Taikai. (Decided,
  §0.) Code quality for this simple project is carried by tests
  that matter, review, and the spec — not by an enforcement
  suite built for larger codebases.
- No custom APM code. The Application Insights agent and
  Actuator/Micrometer are the instrumentation; nobody writes a
  metrics client.
- No self-managed observability infrastructure. No log pipeline
  to run, no metrics database to operate, no dashboard server to
  patch. Azure Monitor is the store, the query engine, and the
  dashboard host.
- No observability features that need a second system to
  understand. If a view cannot be built from run id + Log
  Analytics + Application Insights, it does not belong in v0.1.

## 10. Open questions for review

1. **Deployment shape:** Container Apps (recommended) or App
   Service with REST-only scheduling? The answer decides where
   the CLI shape lives.
2. **Scheduler's touch point:** does the external scheduler call
   REST `POST /runs`, or trigger a CLI job run? Both are
   supported; one should be primary.
3. **Alert recipients and channels:** who gets paged, on what
   channel, for which alerts (§7)? Action Groups need real
   addresses, not placeholders.
4. **Log retention period:** cost decision for the Log Analytics
   workspace — 30, 90, or more days?
5. **Dead-man windows:** the expected schedule window per source
   (for the no-success alert) — operator-provided, per source.
6. **Collection path detail:** Application Insights agent alone,
   or agent plus managed-Prometheus scraping of the Actuator
   Prometheus endpoint? Both work; one should be primary to
   avoid double-counting the same metric from two paths.

---

*Next in sequence: `005_…`, whatever the next topic is. Numbering
continues from here, for markdown and audio alike.*
