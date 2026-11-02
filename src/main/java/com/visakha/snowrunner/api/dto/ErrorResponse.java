package com.visakha.snowrunner.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** The one error shape: only the fields relevant to the error are present, never a trace. */
public record ErrorResponse(ErrorBody error) {

  /** Creates the envelope from a code, a message, and optional state fields. */
  public static ErrorResponse of(
      String code, String message, String runId, String currentStatus, String activeRunId) {
    return new ErrorResponse(new ErrorBody(code, message, runId, currentStatus, activeRunId));
  }

  /** Error body with nullable state fields omitted when irrelevant. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ErrorBody(
      String code, String message, String runId, String currentStatus, String activeRunId) {}
}
