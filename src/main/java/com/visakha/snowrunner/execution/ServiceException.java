package com.visakha.snowrunner.execution;

import com.visakha.snowrunner.domain.Run;
import org.springframework.http.HttpStatus;

/**
 * The one service-layer failure type. It carries the API error code, the HTTP status, and only the
 * envelope fields relevant to that error — the handler renders it, and nothing in it ever contains
 * a stack trace, SQL text, or connection detail.
 */
public class ServiceException extends RuntimeException {

  private final String code;
  private final HttpStatus status;
  private final String runId;
  private final String currentStatus;
  private final String activeRunId;

  private ServiceException(
      String code,
      HttpStatus status,
      String message,
      String runId,
      String currentStatus,
      String activeRunId) {
    super(message);
    this.code = code;
    this.status = status;
    this.runId = runId;
    this.currentStatus = currentStatus;
    this.activeRunId = activeRunId;
  }

  /** Creates a 404 for an unknown workflow. */
  public static ServiceException workflowNotFound(String workflowId) {
    return new ServiceException(
        "WORKFLOW_NOT_FOUND",
        HttpStatus.NOT_FOUND,
        "Unknown workflow '" + workflowId + "'",
        null,
        null,
        null);
  }

  /** Creates a 404 for an unknown run. */
  public static ServiceException runNotFound(String runId) {
    return new ServiceException(
        "RUN_NOT_FOUND", HttpStatus.NOT_FOUND, "Unknown run '" + runId + "'", runId, null, null);
  }

  /** Creates a 409 for a transition the run's current state forbids. */
  public static ServiceException invalidTransition(Run run, String action) {
    return new ServiceException(
        "INVALID_TRANSITION",
        HttpStatus.CONFLICT,
        "Run " + run.runId() + " is " + run.status() + " and cannot be " + action + ".",
        run.runId(),
        run.status().name(),
        null);
  }

  /** Creates a 409 naming the run that blocks a new trigger. */
  public static ServiceException workflowBusy(String activeRunId) {
    return new ServiceException(
        "WORKFLOW_BUSY",
        HttpStatus.CONFLICT,
        "Workflow already has an active run '" + activeRunId + "'.",
        null,
        null,
        activeRunId);
  }

  /** Creates a 400 for a trigger without the scheduler-supplied key. */
  public static ServiceException missingIdempotencyKey() {
    return new ServiceException(
        "MISSING_IDEMPOTENCY_KEY",
        HttpStatus.BAD_REQUEST,
        "POST /runs requires an X-Idempotency-Key header; the application never invents one.",
        null,
        null,
        null);
  }

  /** Creates a 400 for a bad body or query value. */
  public static ServiceException validationFailed(String message) {
    return new ServiceException(
        "VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message, null, null, null);
  }

  public String code() {
    return code;
  }

  public HttpStatus status() {
    return status;
  }

  public String runId() {
    return runId;
  }

  public String currentStatus() {
    return currentStatus;
  }

  public String activeRunId() {
    return activeRunId;
  }
}
