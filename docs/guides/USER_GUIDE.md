# snow-runner — User guide

snow-runner runs the Snowflake (or Oracle-mirrored) ETL by calling
its stored routines in the right order, one workflow at a time,
and keeps a history of every run. It holds no ETL logic itself:
if a mapping changes, the change goes into the routines, not
into this app.

Base URL for all examples: `http://<host>:8080/api/v1`.
Two headers are required on every call (placeholder auth until
Entra ID app roles land — see the DevOps guide):

- `X-Caller-Id`: who is calling (for the audit log).
- `X-Caller-Role`: `scheduler` for control calls,
  `scheduler` or `support` for read calls. A control call with
  the wrong role returns 403.

## Trigger a run

```bash
curl -X POST http://<host>:8080/api/v1/runs \
  -H 'X-Caller-Id: scheduler-1' -H 'X-Caller-Role: scheduler' \
  -H 'Content-Type: application/json' \
  -d '{"workflowId": "TRS_ONGOING", "idempotencyKey": "trs-daily-2026-10-30"}'
```

- `workflowId` is one of the 7 workflows (`GET /workflows` lists
  them with their steps).
- `idempotencyKey` is required and must be stable: the scheduler
  supplies it (workflow + fire time is the suggested shape).
  Replaying the same key returns the existing run with 200
  instead of starting a duplicate. A missing key returns
  `MISSING_IDEMPOTENCY_KEY`.
- 202 means the run was accepted and executed to a terminal or
  paused state (runs are synchronous in v0.1 — keep the HTTP
  timeout longer than the longest step).
- If another run of the same workflow is still RUNNING or
  PAUSED, you get 409 `WORKFLOW_BUSY` with the active run id.
  Wait for it or cancel it; do not force a second run.

## Check a run

```bash
curl http://<host>:8080/api/v1/runs/<runId> \
  -H 'X-Caller-Id: you' -H 'X-Caller-Role: support'
```

The detail shows the run's status, its current steps, and the
counts of succeeded / failed / skipped steps. List runs with
filters and paging:

```bash
curl 'http://<host>:8080/api/v1/runs?workflowId=TRS_ONGOING&status=FAILED&page=0&size=50' \
  -H 'X-Caller-Id: you' -H 'X-Caller-Role: support'
```

Page is 0-based, size defaults to 50 and clamps at 200, newest
first. Attempt history for a run lives at
`GET /runs/<runId>/attempts`.

## Run states and step states

Run: `RUNNING`, `SUCCEEDED`, `FAILED`, `PAUSED`, `CANCELLED`.
Step: `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `SKIPPED`.

A skipped step carries a reason from a closed vocabulary:
`DISABLED` (catalog-disabled step), `FAIL_FAST` (a prior step
failed and execution stopped), `CANCELLED`, `APP_STOPPED` (the
app died mid-run; the step never ran).

## Resume, pause, cancel

```bash
curl -X POST http://<host>:8080/api/v1/runs/<runId>/resume \
  -H 'X-Caller-Id: scheduler-1' -H 'X-Caller-Role: scheduler'
curl -X POST http://<host>:8080/api/v1/runs/<runId>/pause \
  -H 'X-Caller-Id: scheduler-1' -H 'X-Caller-Role: scheduler'
curl -X POST http://<host>:8080/api/v1/runs/<runId>/cancel \
  -H 'X-Caller-Id: scheduler-1' -H 'X-Caller-Role: scheduler'
```

- **Resume** starts a new attempt on the same run id, reusing
  the original run timestamp, and executes only steps that are
  not SUCCEEDED and not disabled. This is how a failed run is
  recovered — never re-trigger with a new idempotency key for
  the same work.
- **Pause** stops after the current step finishes and can be
  resumed. Pausing a PAUSED run is a no-op (200).
- **Cancel** abandons the run permanently. A cancelled run
  cannot be resumed; trigger fresh work instead.
- Pause and cancel never kill an in-flight CALL: the stop takes
  effect between steps only. The worst case is one step's
  duration (the CRE loader was observed at ~15 minutes).
- An illegal transition (e.g. resume a SUCCEEDED run) returns
  409 `INVALID_TRANSITION` with the current status.

## The DQM warning (read before resuming anything by hand)

Four drivers append DQM rows on every execution
(`TRS_ADDR_2`, `ECI_PTY_ADDR`, `FSS_PTY_ADDR`, `WIC_PTY_ADDR`,
marked `rerunSafety: APPEND`). Re-running one of those steps
re-appends its DQM rows — that is a known limitation, not a bug
to work around by re-running. Resume is safe here precisely
because it skips SUCCEEDED steps: a DQM step that already
succeeded is never re-executed by a resume. If the app dies
after a DQM CALL commits but before the step row is persisted,
reconciliation marks the step FAILED and a resume re-executes
it — that one window can double-append. It is diagnosable from
the query tag / client info in the database's query history;
there is no automatic dedup in v0.1.

## Dry-run

```bash
curl -X POST http://<host>:8080/api/v1/workflows/TRS_ONGOING/dry-run \
  -H 'X-Caller-Id: scheduler-1' -H 'X-Caller-Role: scheduler'
```

A dry-run walks the same pipeline minus the caller: it lists
the steps that would run (and preflight results) without
issuing any CALL. It creates no run row, consumes no
idempotency key, and never appears in run history. Dry-run is
scheduler-role only as written.

## List workflows

```bash
curl http://<host>:8080/api/v1/workflows \
  -H 'X-Caller-Id: you' -H 'X-Caller-Role: support'
```

Each workflow lists its steps with enabled flags, rerun
safety, and the dimensions each step touches.

## Which database did a run use?

Every run records its `targetDatabase` (`snowflake` or
`oracle`), the catalog version, the app version, and the tree
version. A run never switches database halfway; switching the
flag is a restart-time operation (see the DevOps guide).

## Troubleshooting

| Symptom | Meaning | What to do |
|---|---|---|
| `MISSING_IDEMPOTENCY_KEY` | No key on `POST /runs`. | Supply a stable scheduler-provided key. |
| `WORKFLOW_NOT_FOUND` | Unknown `workflowId`. | Check `GET /workflows` for the exact id. |
| `WORKFLOW_BUSY` + active run id | A run of this workflow is RUNNING/PAUSED. | Wait, resume, or cancel that run. |
| `INVALID_TRANSITION` + current status | The requested transition is illegal from here. | Check the run's current state first. |
| `RUN_NOT_FOUND` | Unknown run id. | Check the id; ids are UUIDs. |
| Preflight failure naming procedures | A routine is missing from the target schema. | Install the missing routine; the run stops before any CALL. |
| 403 on a control call | Caller role is not `scheduler`. | Use the scheduler identity; support is read-only. |
| 401/unauthenticated | Missing caller headers. | Supply `X-Caller-Id` and `X-Caller-Role`. |
| Run FAILED at a step | The routine raised an error; the message is on the step. | Read the step's error, fix the cause in the routine/data, then resume the same run. |

Errors never include stack traces — only the code, a message,
and, where relevant, the current status or active run id.

## The constitution behind this app

This app is built to the clean-ETL constitution
(`docs/constitution/clean-etl-constitution-guide.md`). What that
means in practice, for the person running it:

- **Runs are stateless.** A run's inputs are the day's staging
  deltas plus reference data. Nothing else. The app keeps no ETL
  state between runs — only run bookkeeping (RUN / RUN_STEP
  rows), which is history, not input.
- **Java never does ETL work.** It calls drivers in catalog
  order and nothing else. It never reads `C_S_*` or `V_*` tables,
  never branches on a flag or diagnostic string, and never
  detects deltas. Delta detection belongs to the separate
  staging workflow that writes the `_D_` tables; history (`_H_`)
  tables are read-only inputs to the procedures, never written
  by anything here.
- **Names say source, dimension, and phase.** A step id like
  `TRS_ADDR_2` and a class like `TrsAddressLoader` tell you the
  workflow, the dimension, and the stage (spine loader versus
  non-spine enrich / exc / dqm). The YAML catalog is the
  contract: it lists the steps, the order, and the schemas —
  never discovered at runtime.
- **Downstream reads through lenses, not deletions.** The MDM
  batch reads `V_MDM_*` views, compare reads `V_CMP_*`, audit
  reads `V_AUDIT_*`. Flagged rows stay in the `C_S_*` tables
  with their reasons; no consumer's view deletes anything for
  another consumer.
- **The run timestamp is trigger time, not a row stamp.**
  `RUN.RUN_TS` records when the run was triggered and is reused
  on resume so a resumed attempt is recognizably the same run.
  Drivers generate their own row stamps internally and receive
  zero arguments from this app today — do not read `RUN.RUN_TS`
  as the timestamp on the landed rows.
- **The run-start bookends are not built yet — read this
  before assuming a re-run starts clean.** The constitution
  calls for every run to start by dropping work tables and
  truncating `C_S_*` tables, then loading fresh. That rule is
  NOT implemented in this app and NOT in the drivers either:
  both sides append today. Until the bookends land (owner still
  to be decided — a run-start step here versus driver changes),
  a re-run does not start from an empty slate, and resume —
  never re-trigger — is the recovery path. The one protection
  that does exist is skip-on-SUCCEEDED: a step that already
  succeeded is never re-executed by a resume, which is what
  keeps APPEND (DQM) steps safe.
