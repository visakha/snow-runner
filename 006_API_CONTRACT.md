# 006 — API contract (v1)

> Companion to [001_SPEC.md](001_SPEC.md) (the orchestrator spec),
> [002_DOMAIN_MODEL.md](002_DOMAIN_MODEL.md) (modeling),
> [003_YAML_CONSUMPTION.md](003_YAML_CONSUMPTION.md) (catalog
> execution), [004_OBSERVABILITY.md](004_OBSERVABILITY.md)
> (touch points and Azure observability), and
> [005_RUN_LIFECYCLE_AND_CONTROL_TABLES.md](005_RUN_LIFECYCLE_AND_CONTROL_TABLES.md)
> (run lifecycle and control tables). Still spec, still no code.
>
> Decisions recorded here (2026-10-28, following the 006
> suggestions audio and the user's answers to its two
> questions):
>
> - **Scheduler provides the idempotency key.** The scheduler
>   team will generate a stable key per scheduled occurrence;
>   the application requires it and never invents one (§6).
> - **Read surface as suggested, approved in full.** Workflow
>   listing, attempt history, and the other read endpoints are
>   in (§3).
> - **Base path `/api/v1`, JSON everywhere**, one run envelope
>   for every run response, async control actions (§1, §4).
> - **Two roles on every endpoint** (§2): scheduler (control +
>   read), support (read-only). Actuator health liveness is the
>   only open endpoint, because the scheduler must check it
>   before anything else exists.
> - **Error model** (§5): machine-readable codes, human-readable
>   messages, current state where relevant, never a stack trace.

## 1. Conventions

- **Base path:** `/api/v1`. All application endpoints live
  under it. Actuator endpoints (`/actuator/…`) are Spring
  standard and are not versioned by this contract (§3).
- **Format:** JSON request and response bodies, UTF-8.
  `Content-Type: application/json` on requests with a body and
  on all responses.
- **Timestamps:** ISO-8601 UTC with `Z` (e.g.
  `2026-10-29T02:00:00Z`). The run timestamp field is the
  Java-owned `P_RUN_TS` (005 §2), rendered the same way.
- **Durations:** milliseconds, as numbers (`durationMs`).
- **Run id in every run response:** any response about a run
  carries `runId` at the top level — including error responses
  about that run (§5).
- **No hypermedia:** responses do not carry link relations.
  The client knows the endpoint shapes from this contract; link
  chasing would add surface for no benefit in v0.1.
- **Trailing slashes:** paths are exact; `/runs/` is not
  `/runs`. Clients use the shapes as written.

## 2. Authentication and authorization

Two roles, matching 001 §9 and 004 §2 principle 8. The mechanism
(Entra ID app roles recommended vs API keys) is still open —
this contract fixes *which role each endpoint requires*, not
*how the role is proven*. When the mechanism lands, it maps to
these two roles and nothing else.

| Role | May call |
|---|---|
| Scheduler | Control endpoints + all read endpoints |
| Support | Read endpoints only |

Rules:

- Every endpoint under `/api/v1` requires an authenticated
  caller. Unauthenticated → 401.
- A support caller on a control endpoint → 403, not a degraded
  response. Read access cannot trigger, resume, pause, or
  cancel anything.
- Every control call is logged with the caller's identity
  (001 §9 audit rule). The `triggeredBy` field on a run is the
  scheduler's (or operator's) identity, not a free-text label.
- Actuator: liveness (`/actuator/health/liveness`) is open —
  the scheduler checks it before authentication exists in the
  picture, and it reveals nothing but "process up". Readiness,
  metrics, and Prometheus exposition require an authenticated
  caller at read level or above (a monitoring identity counts
  as read level).

## 3. Endpoints

### Control endpoints (scheduler role)

| Method and path | Purpose | Success | Notes |
|---|---|---|---|
| `POST /api/v1/runs` | Trigger a workflow | 202 new run; 200 existing run on idempotency retry | Body: `workflowId` (required). Header `X-Idempotency-Key` (required, §6). 409 if the workflow already has an active run (005 §9), with the active run's id. |
| `POST /api/v1/runs/{runId}/resume` | Resume a FAILED or PAUSED run (new attempt, 005 §3) | 202 + run envelope | 409 if SUCCEEDED, CANCELLED, or already RUNNING. |
| `POST /api/v1/runs/{runId}/pause` | Pause a RUNNING run (005 §4) | 202 + run envelope; 200 + envelope if already PAUSED (no-op) | 409 if FAILED or terminal. |
| `POST /api/v1/runs/{runId}/cancel` | Cancel a RUNNING or PAUSED run (005 §4) | 202 + run envelope | 409 if SUCCEEDED, FAILED, or CANCELLED. |
| `POST /api/v1/workflows/{workflowId}/dry-run` | List the workflow's ordered steps with resolved procedure names | 200 + dry-run response | Creates no run, consumes no idempotency key, writes no RUN row (005 §8). |

Control actions are asynchronous: 202 means "accepted, the run
is in its new state" — it does not mean the run (or the
resumed attempt) has finished. The client polls
`GET /runs/{runId}` or relies on the run-failed alert (004 §7).
No webhooks in v0.1: a push notification channel is a future
addition, not assumed here.

Control endpoints take no body except `POST /runs`. Resume,
pause, and cancel need no parameters beyond the run id — the
transition *is* the request, which is why they are separate
endpoints rather than one endpoint with an action word in the
body. Separate endpoints are clearer to authorize (one role
check per endpoint), clearer to log, and clearer to read in a
scheduler script.

### Read endpoints (scheduler or support role)

| Method and path | Purpose | Response |
|---|---|---|
| `GET /api/v1/runs` | List runs | Page envelope (§4) of run summaries (no steps embedded) |
| `GET /api/v1/runs/{runId}` | Run detail | Run envelope (§4) with current steps |
| `GET /api/v1/runs/{runId}/attempts` | Full attempt history | `{ runId, attempts: [ { attemptNo, steps: […] } ] }`, attempts ascending, steps by position |
| `GET /api/v1/workflows` | Catalog as the application sees it | `{ catalogVersion, workflows: [ { workflowId, source, steps: [ { stepId, position, procedureName, enabled, rerunSafety, touches } ] } ] }` |

List filters for `GET /runs` (all optional, combined with AND):
`source`, `workflowId`, `status` (one of the five run states),
`from`, `to` (ISO-8601 timestamps, applied to `createdAt`).
Paging: `page` (0-based, default 0), `size` (default 50, max
200; above 200 is clamped to 200, not rejected — a client
asking for too much gets a page, not an error). Sort is fixed:
`createdAt` descending (newest first). No other sort is
offered in v0.1; a support engineer paging a list wants "latest
first" and nothing else has been asked for.

Unknown `runId` or `workflowId` on any endpoint → 404 with
the corresponding code (§5). Unknown query parameters are
ignored, not rejected — a client passing an extra filter it
guessed at gets an answer, not a lecture.

### Actuator endpoints (not under `/api/v1`)

| Path | Access | Purpose |
|---|---|---|
| `/actuator/health/liveness` | Open | Scheduler's alive check (004 §6, 001 §10) |
| `/actuator/health/readiness` | Authenticated (read level+) | Snowflake reachability + control tables present + reconciliation complete (005 §6) |
| `/actuator/metrics`, `/actuator/prometheus` | Authenticated (read level+) | Metrics for Azure Monitor (004 §4) |

## 4. Envelopes

### Run envelope (every run response)

| Field | Type | Meaning |
|---|---|---|
| runId | string | UUID (005 §2). |
| workflowId | string | E.g. TRS_ONGOING. |
| source | string | TRS / ECI / FSS / WIC / TTF / CRE. |
| status | string | One of the five run states (005 §1). |
| runTimestamp | string (ISO-8601) | The Java-owned `P_RUN_TS`. |
| attemptNo | number | Current attempt number (005 §3). |
| triggeredBy | string | Caller identity (audit). |
| createdAt / updatedAt | string (ISO-8601) | From the RUN row. |
| endedAt | string (ISO-8601) or null | Null until SUCCEEDED, FAILED, or CANCELLED. |
| stepsSucceeded / stepsFailed / stepsSkipped | number | Derived from current steps at read time — during a run these are progress counts; at the end they equal the stored finals (005 §2). One rule, no null-vs-final confusion. |
| steps | array (detail and resume only) | Current steps (§4 step object). Present on `GET /runs/{runId}` and on resume responses, so the caller sees what will run; omitted on list summaries and on trigger/pause/cancel responses. The caller that wants steps asks for the detail. |
| activeRunId | string (409 only) | On WORKFLOW_BUSY, the blocking active run's id. |

### Step object (exactly the 005 §2 columns, no more, no less)

| Field | Type | Meaning |
|---|---|---|
| stepId | string | E.g. TRS_PTY_ONGOING. |
| position | number | Catalog position. |
| procedureName | string | Fully qualified, as resolved. |
| status | string | One of the five step states. |
| queryId | string or null | Snowflake query id, on SUCCEEDED/FAILED rows. |
| startedAt / endedAt | string (ISO-8601) or null | |
| durationMs | number or null | |
| errorMessage | string or null | Verbatim, on FAILED rows. |
| skippedReason | string or null | DISABLED / FAIL_FAST / CANCELLED / APP_STOPPED, on SKIPPED rows. |

### Page envelope (`GET /runs`)

`{ content: [run summaries], page, size, totalElements,
totalPages }`. A run summary is the run envelope without
`steps`.

### Dry-run response (`POST /workflows/{id}/dry-run`)

`{ workflowId, catalogVersion, dryRun: true, steps: [ {
stepId, position, procedureName, enabled, rerunSafety } ] }`.
The `dryRun: true` field exists so no consumer can mistake this
for a run: there is no `runId` in the response at all, by
design.

### Examples (shape only, values illustrative)

Trigger, new run:

```json
{
  "runId": "9f2c…",
  "workflowId": "TRS_ONGOING",
  "source": "TRS",
  "status": "RUNNING",
  "runTimestamp": "2026-10-29T02:00:00Z",
  "attemptNo": 1,
  "triggeredBy": "scheduler-prod",
  "createdAt": "2026-10-29T02:00:00Z",
  "updatedAt": "2026-10-29T02:00:00Z",
  "endedAt": null,
  "stepsSucceeded": 0,
  "stepsFailed": 0,
  "stepsSkipped": 0
}
```

Illegal transition:

```json
{
  "error": {
    "code": "INVALID_TRANSITION",
    "message": "Run 9f2c… is SUCCEEDED and cannot be resumed.",
    "runId": "9f2c…",
    "currentStatus": "SUCCEEDED"
  }
}
```

## 5. Error model

One shape for every error:

```json
{ "error": { "code": "...", "message": "...",
  "runId": "…", "currentStatus": "…", "activeRunId": "…" } }
```

Only the fields relevant to the error are present. Codes:

| Code | HTTP | When | Extra fields |
|---|---|---|---|
| VALIDATION_FAILED | 400 | Bad body or query (missing `workflowId`, malformed timestamp, unknown `status` filter value) | — |
| MISSING_IDEMPOTENCY_KEY | 400 | `POST /runs` without `X-Idempotency-Key` (§6) | — |
| WORKFLOW_NOT_FOUND | 404 | Unknown `workflowId` | — |
| RUN_NOT_FOUND | 404 | Unknown `runId` | — |
| WORKFLOW_BUSY | 409 | Workflow already has a RUNNING or PAUSED run (005 §9) | `activeRunId` |
| INVALID_TRANSITION | 409 | Resume/pause/cancel not allowed from the run's current state (005 §1) | `runId`, `currentStatus` |
| UNAUTHENTICATED | 401 | No or invalid credentials | — |
| FORBIDDEN | 403 | Authenticated, wrong role (§2) | — |

Messages are human-readable and specific ("Run … is SUCCEEDED
and cannot be resumed", not "Invalid state"). Responses never
contain stack traces, exception class names, SQL text, or
connection details — an error response that leaks internals is
a bug, not a detail. Server-side, the same failure is logged in
full with the run id (004 §3); the client gets the code, the
message, and the state it needs to decide what to do next.

## 6. Idempotency

- `POST /runs` requires `X-Idempotency-Key`. Missing key →
  400 `MISSING_IDEMPOTENCY_KEY`. The application never invents
  a key: a generated key would defeat the purpose, because a
  retried trigger without a caller-supplied key is
  indistinguishable from a genuinely new trigger.
- **Scope is (workflowId, key).** The same key for a TRS run
  and an ECI run must not collide, so the lookup is by workflow
  plus key, and the key is stored on the RUN row (005 §2).
- **A retry returns the existing run with 200**, not an error
  and not a new run: same `runId`, same `runTimestamp`, current
  status. The scheduler treats 200-on-trigger as "already
  running (or already done) under this key" and proceeds to
  poll that run.
- **Key generation is the scheduler team's responsibility**
  (decided 2026-10-28 — the user will ask the scheduler team
  to provide it). The contract requires only that the key be
  stable per scheduled occurrence and unique per (workflow,
  occurrence). A natural shape is workflow plus scheduled fire
  time, but the format is the scheduler's choice — the key is
  opaque to the application, which never parses it.
- Idempotency does **not** apply to resume/pause/cancel: those
  are transitions on a run id, and repeat behavior is defined
  by the transition table itself (pause on PAUSED → 200 no-op;
  resume on RUNNING → 409; cancel on CANCELLED → 409).

## 7. Versioning

- Additive changes (a new field on an envelope, a new optional
  filter, a new read endpoint) never bump the version. Clients
  ignore unknown fields — that rule is part of the contract,
  not a hope.
- A breaking change (a removed or retyped field, a changed
  transition rule) means `/api/v2`, with `/api/v1` kept until
  the scheduler has moved. No silent breaking change, ever.
- Actuator paths are Spring-managed and are not versioned by
  this contract.

## 8. Open questions for review

1. **Dry-run role:** dry-run is scheduler-only in §3 (it sits
   with the control endpoints, as suggested). Should support
   also be able to dry-run a workflow for inspection, or is
   that a control act? Decided scheduler-only here; confirm on
   review.
2. **Auth mechanism detail** (carried from 001 §13 question 5
   and 004 §10): Entra ID app roles vs API keys, and which
   Entra ID group maps to the support role. The roles in §2 are
   fixed regardless.
3. **Scheduler key format confirmation:** the scheduler team
   confirms the key shape it will send (workflow + fire time
   suggested, §6) — the application accepts any opaque string,
   but the scheduler side needs to commit to stability.

## 9. What this closes

- The 006 suggestions audio's two questions: scheduler key
  (§6, scheduler team's responsibility) and read surface (§3,
  approved in full).
- 001 §9's endpoint sketch is now a contract: every endpoint,
  role, status code, and envelope field is specified.
- 005 §10's "shape, not contract" is now the contract: the
  read endpoints read exactly the tables and view 005 defines.

Carried forward, not closed: auth mechanism detail (§8
question 2), scheduler key format confirmation (§8 question 3),
and 004 §10's remaining items (alert recipients, retention,
dead-man windows, collection path detail).

---

*Next in sequence: `007_…`, whatever the next topic is.*
