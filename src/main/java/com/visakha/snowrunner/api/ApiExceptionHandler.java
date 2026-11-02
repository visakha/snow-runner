package com.visakha.snowrunner.api;

import com.visakha.snowrunner.api.dto.ErrorResponse;
import com.visakha.snowrunner.execution.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Renders every failure as the one error shape. Service failures carry their own code and state;
 * anything unexpected becomes a bare 500 — the client gets a code, never a stack trace, while the
 * server log keeps the full failure.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  /** Renders a service failure with its code and relevant state fields. */
  @ExceptionHandler(ServiceException.class)
  public ResponseEntity<ErrorResponse> handleService(ServiceException e) {
    return ResponseEntity.status(e.status())
        .body(
            ErrorResponse.of(
                e.code(), e.getMessage(), e.runId(), e.currentStatus(), e.activeRunId()));
  }

  /** Renders an unexpected failure without leaking internals. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
    log.error("Unexpected failure", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ErrorResponse.of("INTERNAL_ERROR", "Unexpected failure.", null, null, null));
  }
}
