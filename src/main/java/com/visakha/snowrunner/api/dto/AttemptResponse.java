package com.visakha.snowrunner.api.dto;

import com.visakha.snowrunner.execution.RunQueryService.AttemptHistory;
import java.util.List;

/** Attempt history: every step row grouped by attempt, attempts ascending. */
public record AttemptResponse(String runId, List<Attempt> attempts) {

  /** Maps attempt histories to their response shape. */
  public static AttemptResponse from(String runId, List<AttemptHistory> histories) {
    List<Attempt> attempts =
        histories.stream()
            .map(
                history ->
                    new Attempt(
                        history.attemptNo(),
                        history.steps().stream().map(StepResponse::from).toList()))
            .toList();
    return new AttemptResponse(runId, attempts);
  }

  /** One attempt's steps. */
  public record Attempt(int attemptNo, List<StepResponse> steps) {}
}
