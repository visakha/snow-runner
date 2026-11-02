package com.visakha.snowrunner.api;

import com.visakha.snowrunner.api.dto.AttemptResponse;
import com.visakha.snowrunner.api.dto.PageResponse;
import com.visakha.snowrunner.api.dto.RunResponse;
import com.visakha.snowrunner.api.dto.TriggerRunRequest;
import com.visakha.snowrunner.domain.RunStatus;
import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.RunExecutor;
import com.visakha.snowrunner.execution.RunQueryService;
import com.visakha.snowrunner.execution.ServiceException;
import com.visakha.snowrunner.execution.TriggerResult;
import com.visakha.snowrunner.persistence.RunFilter;
import com.visakha.snowrunner.persistence.RunPage;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Run endpoints: control (scheduler) plus reads (scheduler or support). */
@RestController
@RequestMapping("/api/v1")
public class RunController {

  private static final Logger log = LoggerFactory.getLogger(RunController.class);

  private final RunExecutor executor;
  private final RunQueryService queries;

  /** Creates the controller. */
  public RunController(RunExecutor executor, RunQueryService queries) {
    this.executor = executor;
    this.queries = queries;
  }

  /** Triggers a workflow: 202 for a new run, 200 for an idempotency retry. */
  @PostMapping("/runs")
  public ResponseEntity<RunResponse> trigger(
      @RequestBody(required = false) TriggerRunRequest body,
      @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
      HttpServletRequest request) {
    if (body == null || body.workflowId() == null || body.workflowId().isBlank()) {
      throw ServiceException.validationFailed("Body must carry a workflowId.");
    }
    CallerIdentity caller = caller(request);
    TriggerResult result = executor.trigger(body.workflowId(), idempotencyKey, caller.id());
    log.info(
        "control trigger workflow={} by={} created={}",
        body.workflowId(),
        caller.id(),
        result.created());
    HttpStatus status = result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK;
    return ResponseEntity.status(status).body(RunResponse.from(result.run(), false));
  }

  /** Resumes a FAILED or PAUSED run: 202 with steps, so the caller sees what will run. */
  @PostMapping("/runs/{runId}/resume")
  public ResponseEntity<RunResponse> resume(
      @PathVariable String runId, HttpServletRequest request) {
    CallerIdentity caller = caller(request);
    log.info("control resume run={} by={}", runId, caller.id());
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(RunResponse.from(executor.resume(runId, caller.id()), true));
  }

  /** Pauses a RUNNING run: 202, or 200 when already PAUSED (no-op). */
  @PostMapping("/runs/{runId}/pause")
  public ResponseEntity<RunResponse> pause(@PathVariable String runId, HttpServletRequest request) {
    CallerIdentity caller = caller(request);
    log.info("control pause run={} by={}", runId, caller.id());
    var current = queries.getRun(runId);
    if (current.status() == RunStatus.PAUSED) {
      return ResponseEntity.ok(RunResponse.from(current, false));
    }
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(RunResponse.from(executor.pause(runId), false));
  }

  /** Cancels a RUNNING or PAUSED run: 202. Terminal, never resumable. */
  @PostMapping("/runs/{runId}/cancel")
  public ResponseEntity<RunResponse> cancel(
      @PathVariable String runId, HttpServletRequest request) {
    CallerIdentity caller = caller(request);
    log.info("control cancel run={} by={}", runId, caller.id());
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(RunResponse.from(executor.cancel(runId), false));
  }

  /** Lists runs, newest first. Unknown query parameters are ignored, not rejected. */
  @GetMapping("/runs")
  public PageResponse listRuns(
      @RequestParam(required = false) String source,
      @RequestParam(required = false) String workflowId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    RunFilter filter =
        new RunFilter(
            parseSource(source),
            workflowId,
            parseStatus(status),
            parseInstant(from),
            parseInstant(to));
    RunPage result = queries.listRuns(filter, page, size);
    int safeSize = size <= 0 ? 50 : Math.min(size, 200);
    int safePage = Math.max(page, 0);
    long totalPages = safeSize == 0 ? 0 : (result.totalElements() + safeSize - 1) / safeSize;
    return new PageResponse(
        result.content().stream().map(run -> RunResponse.from(run, false)).toList(),
        safePage,
        safeSize,
        result.totalElements(),
        totalPages);
  }

  /** Returns run detail with current steps. */
  @GetMapping("/runs/{runId}")
  public RunResponse getRun(@PathVariable String runId) {
    return RunResponse.from(queries.getRun(runId), true);
  }

  /** Returns full attempt history, attempts ascending. */
  @GetMapping("/runs/{runId}/attempts")
  public AttemptResponse getAttempts(@PathVariable String runId) {
    return AttemptResponse.from(runId, queries.getAttempts(runId));
  }

  private static CallerIdentity caller(HttpServletRequest request) {
    return (CallerIdentity) request.getAttribute(CallerIdentityFilter.CALLER_ATTRIBUTE);
  }

  private static Source parseSource(String value) {
    if (value == null) {
      return null;
    }
    try {
      return Source.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw ServiceException.validationFailed("Unknown source filter '" + value + "'.");
    }
  }

  private static RunStatus parseStatus(String value) {
    if (value == null) {
      return null;
    }
    try {
      return RunStatus.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw ServiceException.validationFailed("Unknown status filter '" + value + "'.");
    }
  }

  private static Instant parseInstant(String value) {
    if (value == null) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException e) {
      throw ServiceException.validationFailed("Malformed timestamp '" + value + "'.");
    }
  }
}
