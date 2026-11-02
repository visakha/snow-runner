package com.visakha.snowrunner.execution;

import com.visakha.snowrunner.catalog.StepCatalog;
import com.visakha.snowrunner.catalog.StepDefinition;
import com.visakha.snowrunner.catalog.WorkflowDefinition;
import com.visakha.snowrunner.domain.Clock;
import com.visakha.snowrunner.domain.Run;
import com.visakha.snowrunner.domain.RunId;
import com.visakha.snowrunner.domain.RunStatus;
import com.visakha.snowrunner.domain.RunStep;
import com.visakha.snowrunner.domain.RunTimestamp;
import com.visakha.snowrunner.domain.SkipReason;
import com.visakha.snowrunner.domain.StepStatus;
import com.visakha.snowrunner.infra.config.CatalogProperties;
import com.visakha.snowrunner.persistence.RunRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executes workflows: sequential, fail-fast, one driver CALL per step. Runs execute synchronously
 * in the trigger request — v0.1 has no queue and no execution threads, so a 202 means "accepted and
 * executed to a terminal or paused state", and the scheduler's HTTP client needs a timeout longer
 * than the longest step (the CRE loader was observed at ~15 minutes). QUEUED stays reserved for a
 * future version that queues work.
 *
 * <p>Pause and cancel are cooperative and between steps only: the in-flight CALL is never
 * interrupted, because a completed step is always a known state and a killed step is not. Requests
 * arrive on other HTTP threads and are held in a stop-request map the loop checks between steps;
 * that map is coordination state, not execution threading.
 *
 * <p>Constitution notes. Runs are stateless with respect to the ETL: inputs are the catalog, the
 * control tables, and the ETL connection, and no step reads another run's leftovers. The run-start
 * bookends the constitution calls for — drop work tables, truncate CS tables — are NOT implemented
 * here and are NOT in the drivers either; both sides append today, so a re-run must not be assumed
 * to start clean. Adding the bookends is pending a decision on owner (a run-start step here versus
 * driver changes); until then, resume — never re-trigger — is the recovery path, and APPEND steps
 * are never re-executed once SUCCEEDED.
 */
@Component
public class RunExecutor {

  private static final Logger log = LoggerFactory.getLogger(RunExecutor.class);
  private static final String APP_VERSION = "0.1.0";

  private final StepCatalog catalog;
  private final RunRepository repository;
  private final ProcedureCaller caller;
  private final PreflightService preflight;
  private final ResumePolicy resumePolicy;
  private final PhaseHandlerRegistry registry;
  private final Clock clock;
  private final String treeVersion;
  private final Counter runsStarted;
  private final Counter runsSucceeded;
  private final Counter runsFailed;
  private final Counter stepsFailed;
  private final ConcurrentHashMap<String, StopRequest> stopRequests = new ConcurrentHashMap<>();

  /** Creates the executor from its collaborators. */
  public RunExecutor(
      StepCatalog catalog,
      RunRepository repository,
      ProcedureCaller caller,
      PreflightService preflight,
      ResumePolicy resumePolicy,
      PhaseHandlerRegistry registry,
      Clock clock,
      CatalogProperties catalogProperties,
      MeterRegistry meters) {
    this.catalog = catalog;
    this.repository = repository;
    this.caller = caller;
    this.preflight = preflight;
    this.resumePolicy = resumePolicy;
    this.registry = registry;
    this.clock = clock;
    this.treeVersion = catalogProperties.treeVersion();
    this.runsStarted = meters.counter("snowrunner.runs.started");
    this.runsSucceeded = meters.counter("snowrunner.runs.succeeded");
    this.runsFailed = meters.counter("snowrunner.runs.failed");
    this.stepsFailed = meters.counter("snowrunner.steps.failed");
  }

  /** Triggers a workflow, or returns the existing run on an idempotency retry. */
  public TriggerResult trigger(String workflowId, String idempotencyKey, String triggeredBy) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw ServiceException.missingIdempotencyKey();
    }
    WorkflowDefinition workflow =
        catalog
            .workflow(workflowId)
            .orElseThrow(() -> ServiceException.workflowNotFound(workflowId));
    var existing = repository.findByWorkflowAndKey(workflowId, idempotencyKey);
    if (existing.isPresent()) {
      return new TriggerResult(withCurrentSteps(existing.get()), false);
    }
    repository
        .findActiveByWorkflow(workflowId)
        .ifPresent(
            active -> {
              throw ServiceException.workflowBusy(active.runId());
            });
    Instant now = clock.now();
    Run run =
        Run.create(
            RunId.generate(),
            workflowId,
            workflow.source(),
            new RunTimestamp(now),
            catalog.targetDatabase(),
            catalog.catalogVersion(),
            APP_VERSION,
            treeVersion,
            triggeredBy,
            idempotencyKey,
            now);
    // Explicit insert, not save(): Spring Data JDBC treats an assigned String id
    // as an existing row, so save() here would issue an UPDATE that matches
    // nothing and silently persist nothing. Later saves are updates of a row
    // that exists by then.
    repository.insertRun(run);
    for (StepDefinition step : workflow.steps()) {
      if (step.enabled()) {
        repository.insertStep(
            RunStep.pending(
                run.runId(),
                step.stepId().value(),
                1,
                step.position(),
                step.procedureName().qualified()));
      } else {
        repository.insertStep(
            RunStep.pending(
                    run.runId(),
                    step.stepId().value(),
                    1,
                    step.position(),
                    step.procedureName().qualified())
                .skipped(SkipReason.DISABLED));
      }
    }
    log.info("run {} triggered workflow {} by {}", run.runId(), workflowId, triggeredBy);
    runsStarted.increment();
    List<StepDefinition> enabled =
        workflow.steps().stream().filter(StepDefinition::enabled).toList();
    List<StepDefinition> missing = preflight.missingSteps(enabled);
    if (!missing.isEmpty()) {
      failPreflight(run, missing);
      return new TriggerResult(withCurrentSteps(run), true);
    }
    registry
        .hooksFor(workflow.source(), LifecycleRole.PREFLIGHT)
        .forEach(hook -> hook.preflight(run));
    executeLoop(run, enabled, 1);
    return new TriggerResult(withCurrentSteps(run), true);
  }

  /** Resumes a FAILED or PAUSED run as a new attempt reusing the original timestamp. */
  public Run resume(String runId, String triggeredBy) {
    Run run = repository.findById(runId).orElseThrow(() -> ServiceException.runNotFound(runId));
    if (run.status() != RunStatus.FAILED && run.status() != RunStatus.PAUSED) {
      throw ServiceException.invalidTransition(run, "resumed");
    }
    // A stop request from the previous attempt must not leak into the new one.
    stopRequests.remove(runId);
    WorkflowDefinition workflow =
        catalog
            .workflow(run.workflowId())
            .orElseThrow(() -> ServiceException.workflowNotFound(run.workflowId()));
    List<RunStep> current = repository.findCurrentSteps(runId);
    List<StepDefinition> toRun = resumePolicy.stepsToRun(current, workflow.steps());
    int attempt = run.currentAttemptNo() + 1;
    run.currentAttemptNo(attempt);
    run.status(RunStatus.RUNNING);
    run.endedAt(null);
    run.errorMessage(null);
    run.updatedAt(clock.now());
    repository.save(run);
    for (StepDefinition step : toRun) {
      repository.insertStep(
          RunStep.pending(
              runId,
              step.stepId().value(),
              attempt,
              step.position(),
              step.procedureName().qualified()));
    }
    log.info("run {} resumed attempt {} by {}", runId, attempt, triggeredBy);
    runsStarted.increment();
    List<StepDefinition> missing = preflight.missingSteps(toRun);
    if (!missing.isEmpty()) {
      failPreflight(run, missing);
      return withCurrentSteps(run);
    }
    executeLoop(run, toRun, attempt);
    return withCurrentSteps(run);
  }

  /** Requests a pause: the loop stops after the current step and the run becomes PAUSED. */
  public Run pause(String runId) {
    Run run = repository.findById(runId).orElseThrow(() -> ServiceException.runNotFound(runId));
    if (run.status() != RunStatus.RUNNING) {
      throw ServiceException.invalidTransition(run, "paused");
    }
    stopRequests.put(runId, StopRequest.PAUSE);
    log.info("run {} pause requested", runId);
    return run;
  }

  /** Cancels a RUNNING or PAUSED run; terminal, never resumable. */
  public Run cancel(String runId) {
    Run run = repository.findById(runId).orElseThrow(() -> ServiceException.runNotFound(runId));
    if (run.status() == RunStatus.RUNNING) {
      stopRequests.put(runId, StopRequest.CANCEL);
      log.info("run {} cancel requested", runId);
      return run;
    }
    if (run.status() == RunStatus.PAUSED) {
      List<RunStep> current = repository.findCurrentSteps(runId);
      for (RunStep row : current) {
        if (row.status() == StepStatus.PENDING) {
          repository.updateStep(row.skipped(SkipReason.CANCELLED));
        }
      }
      finishRun(run, RunStatus.CANCELLED, null);
      log.info("run {} cancelled while paused", runId);
      return withCurrentSteps(run);
    }
    throw ServiceException.invalidTransition(run, "cancelled");
  }

  /** Lists the workflow's ordered steps with resolved names. No run row, no CALL. */
  public DryRunResult dryRun(String workflowId) {
    WorkflowDefinition workflow =
        catalog
            .workflow(workflowId)
            .orElseThrow(() -> ServiceException.workflowNotFound(workflowId));
    String dryRunId = UUID.randomUUID().toString();
    log.info("DRY_RUN_LISTED dryRunId={} workflowId={}", dryRunId, workflowId);
    List<DryStep> steps =
        workflow.steps().stream()
            .map(
                step ->
                    new DryStep(
                        step.stepId().value(),
                        step.position(),
                        step.procedureName().qualified(),
                        step.enabled(),
                        step.rerunSafety()))
            .toList();
    return new DryRunResult(workflowId, catalog.catalogVersion(), steps);
  }

  private void executeLoop(Run run, List<StepDefinition> steps, int attempt) {
    RunTimestamp runTs = new RunTimestamp(run.runTs());
    for (StepDefinition step : steps) {
      StopRequest stop = stopRequests.remove(run.runId());
      if (stop == StopRequest.PAUSE) {
        run.status(RunStatus.PAUSED);
        run.updatedAt(clock.now());
        repository.save(run);
        log.info("run {} paused after step {}", run.runId(), step.stepId().value());
        return;
      }
      if (stop == StopRequest.CANCEL) {
        cancelRemaining(run, steps, step, attempt);
        return;
      }
      RunStep row = findAttemptRow(run.runId(), step.stepId().value(), attempt);
      StepContext context = new StepContext(run, step, attempt);
      List<PhaseHandler> handlers = registry.handlersFor(step, run.source());
      handlers.forEach(handler -> handler.beforeStep(context));
      RunStep started = row.started(clock.now());
      repository.updateStep(started);
      StepCall call = StepCall.resolve(run.runId(), step, runTs, run.source());
      try {
        CallResult result = caller.call(call);
        Instant end = clock.now();
        RunStep finished = started.finished(StepStatus.SUCCEEDED, result.queryId(), end, null);
        repository.updateStep(finished);
        handlers.forEach(handler -> handler.afterStep(context, finished));
        run.updatedAt(end);
        repository.save(run);
        log.info(
            "run {} step {} succeeded queryId={} durationMs={}",
            run.runId(),
            step.stepId().value(),
            result.queryId(),
            result.durationMs());
      } catch (ProcedureCallException e) {
        Instant end = clock.now();
        RunStep failed = started.finished(StepStatus.FAILED, e.queryId(), end, e.getMessage());
        repository.updateStep(failed);
        handlers.forEach(handler -> handler.afterStep(context, failed));
        stepsFailed.increment();
        failFastRemaining(run, steps, step, attempt);
        finishRun(run, RunStatus.FAILED, e.getMessage());
        log.info("run {} step {} failed: {}", run.runId(), step.stepId().value(), e.getMessage());
        return;
      }
    }
    registry.hooksFor(run.source(), LifecycleRole.FLUSH).forEach(hook -> hook.flush(run));
    finishRun(run, RunStatus.SUCCEEDED, null);
  }

  private void cancelRemaining(
      Run run, List<StepDefinition> steps, StepDefinition current, int attempt) {
    boolean past = false;
    for (StepDefinition step : steps) {
      if (step == current) {
        past = true;
      }
      if (past) {
        RunStep row = findAttemptRow(run.runId(), step.stepId().value(), attempt);
        if (row.status() == StepStatus.PENDING) {
          repository.updateStep(row.skipped(SkipReason.CANCELLED));
        }
      }
    }
    finishRun(run, RunStatus.CANCELLED, null);
    log.info("run {} cancelled", run.runId());
  }

  private void failFastRemaining(
      Run run, List<StepDefinition> steps, StepDefinition failed, int attempt) {
    boolean past = false;
    for (StepDefinition step : steps) {
      if (step == failed) {
        past = true;
        continue;
      }
      if (past) {
        RunStep row = findAttemptRow(run.runId(), step.stepId().value(), attempt);
        if (row.status() == StepStatus.PENDING) {
          repository.updateStep(row.skipped(SkipReason.FAIL_FAST));
        }
      }
    }
  }

  private void failPreflight(Run run, List<StepDefinition> missing) {
    String names =
        missing.stream()
            .map(step -> step.procedureName().qualified())
            .reduce((left, right) -> left + ", " + right)
            .orElse("");
    String message = "Preflight failed, missing procedures: " + names;
    finishRun(run, RunStatus.FAILED, message);
    log.info("run {} {}", run.runId(), message);
  }

  private void finishRun(Run run, RunStatus status, String errorMessage) {
    // A finished run keeps no pending stop request: without this, a pause
    // requested during a preflight-failed trigger would pause the later resume.
    stopRequests.remove(run.runId());
    run.status(status);
    run.updatedAt(clock.now());
    if (status == RunStatus.SUCCEEDED
        || status == RunStatus.FAILED
        || status == RunStatus.CANCELLED) {
      run.endedAt(run.updatedAt());
    }
    run.errorMessage(errorMessage);
    List<RunStep> current = repository.findCurrentSteps(run.runId());
    run.stepsSucceeded(count(current, StepStatus.SUCCEEDED));
    run.stepsFailed(count(current, StepStatus.FAILED));
    run.stepsSkipped(count(current, StepStatus.SKIPPED));
    repository.save(run);
    registry.hooksFor(run.source(), LifecycleRole.POSTFLIGHT).forEach(hook -> hook.postflight(run));
    if (status == RunStatus.SUCCEEDED) {
      runsSucceeded.increment();
    } else if (status == RunStatus.FAILED) {
      runsFailed.increment();
    }
  }

  private RunStep findAttemptRow(String runId, String stepId, int attempt) {
    return repository.findStepsByRun(runId).stream()
        .filter(row -> row.stepId().equals(stepId) && row.attemptNo() == attempt)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No step row for run " + runId + " step " + stepId + " attempt " + attempt));
  }

  private Run withCurrentSteps(Run run) {
    run.currentSteps(repository.findCurrentSteps(run.runId()));
    return run;
  }

  private static int count(List<RunStep> steps, StepStatus status) {
    return (int) steps.stream().filter(step -> step.status() == status).count();
  }

  private enum StopRequest {
    PAUSE,
    CANCEL
  }
}
