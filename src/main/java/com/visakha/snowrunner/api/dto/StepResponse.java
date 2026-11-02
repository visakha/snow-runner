package com.visakha.snowrunner.api.dto;

import com.visakha.snowrunner.domain.RunStep;
import java.time.Instant;

/** One step object: exactly the 005 §2 columns, no more, no less. Nulls are serialized. */
public record StepResponse(
    String stepId,
    int position,
    String procedureName,
    String status,
    String queryId,
    Instant startedAt,
    Instant endedAt,
    Long durationMs,
    String errorMessage,
    String skippedReason) {

  /** Maps a step row to its response shape. */
  public static StepResponse from(RunStep row) {
    return new StepResponse(
        row.stepId(),
        row.position(),
        row.procedureName(),
        row.status().name(),
        row.queryId(),
        row.startedAt(),
        row.endedAt(),
        row.durationMs(),
        row.errorMessage(),
        row.skippedReason() == null ? null : row.skippedReason().name());
  }
}
