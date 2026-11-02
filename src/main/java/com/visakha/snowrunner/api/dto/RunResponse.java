package com.visakha.snowrunner.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.visakha.snowrunner.domain.Run;
import com.visakha.snowrunner.domain.RunStep;
import com.visakha.snowrunner.domain.StepStatus;
import java.time.Instant;
import java.util.List;

/**
 * The run envelope (006 §4). Counts come from stored finals once the run has ended, and from
 * current steps while it is in flight — one rule, no null-vs-final confusion. Steps are present
 * only on detail and resume responses, omitted everywhere else.
 */
public record RunResponse(
    String runId,
    String workflowId,
    String source,
    String status,
    Instant runTimestamp,
    int attemptNo,
    String triggeredBy,
    Instant createdAt,
    Instant updatedAt,
    Instant endedAt,
    int stepsSucceeded,
    int stepsFailed,
    int stepsSkipped,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<StepResponse> steps) {

  /** Maps a run to its envelope, with steps only when asked for. */
  public static RunResponse from(Run run, boolean includeSteps) {
    List<RunStep> current = run.currentSteps();
    int succeeded =
        run.stepsSucceeded() != null ? run.stepsSucceeded() : count(current, StepStatus.SUCCEEDED);
    int failed = run.stepsFailed() != null ? run.stepsFailed() : count(current, StepStatus.FAILED);
    int skipped =
        run.stepsSkipped() != null ? run.stepsSkipped() : count(current, StepStatus.SKIPPED);
    List<StepResponse> stepResponses =
        includeSteps ? current.stream().map(StepResponse::from).toList() : null;
    return new RunResponse(
        run.runId(),
        run.workflowId(),
        run.source().name(),
        run.status().name(),
        run.runTs(),
        run.currentAttemptNo(),
        run.triggeredBy(),
        run.createdAt(),
        run.updatedAt(),
        run.endedAt(),
        succeeded,
        failed,
        skipped,
        stepResponses);
  }

  private static int count(List<RunStep> steps, StepStatus status) {
    return (int) steps.stream().filter(step -> step.status() == status).count();
  }
}
