package com.visakha.snowrunner.domain;

import java.time.Instant;

/**
 * One RUN_STEP row (005 §2): one step, one attempt. A record, because a row is replaced wholesale
 * on each transition rather than mutated in place.
 */
public record RunStep(
    String runId,
    String stepId,
    int attemptNo,
    int position,
    String procedureName,
    StepStatus status,
    String queryId,
    Instant startedAt,
    Instant endedAt,
    Long durationMs,
    String errorMessage,
    SkipReason skippedReason) {

  /** Creates the PENDING row written at run creation or at resume for a step about to run. */
  public static RunStep pending(
      String runId, String stepId, int attemptNo, int position, String procedureName) {
    return new RunStep(
        runId,
        stepId,
        attemptNo,
        position,
        procedureName,
        StepStatus.PENDING,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  /** Returns this row with a new status, keeping everything else. */
  public RunStep withStatus(StepStatus newStatus) {
    return new RunStep(
        runId,
        stepId,
        attemptNo,
        position,
        procedureName,
        newStatus,
        queryId,
        startedAt,
        endedAt,
        durationMs,
        errorMessage,
        skippedReason);
  }

  /** Returns this row marked started. */
  public RunStep started(Instant now) {
    return new RunStep(
        runId,
        stepId,
        attemptNo,
        position,
        procedureName,
        StepStatus.RUNNING,
        queryId,
        now,
        endedAt,
        durationMs,
        errorMessage,
        skippedReason);
  }

  /** Returns this row marked finished with the caller's outcome. */
  public RunStep finished(
      StepStatus outcome, String newQueryId, Instant end, String newErrorMessage) {
    return new RunStep(
        runId,
        stepId,
        attemptNo,
        position,
        procedureName,
        outcome,
        newQueryId,
        startedAt,
        end,
        startedAt == null ? null : end.toEpochMilli() - startedAt.toEpochMilli(),
        newErrorMessage,
        skippedReason);
  }

  /** Returns this row marked skipped for a stated reason. */
  public RunStep skipped(SkipReason reason) {
    return new RunStep(
        runId,
        stepId,
        attemptNo,
        position,
        procedureName,
        StepStatus.SKIPPED,
        queryId,
        startedAt,
        endedAt,
        durationMs,
        errorMessage,
        reason);
  }
}
