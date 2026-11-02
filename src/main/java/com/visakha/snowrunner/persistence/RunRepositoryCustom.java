package com.visakha.snowrunner.persistence;

import com.visakha.snowrunner.domain.Run;
import com.visakha.snowrunner.domain.RunStep;
import java.util.List;
import java.util.Optional;

/**
 * Control-table queries beyond the aggregate root. Implemented by RunRepositoryImpl with explicit
 * SQL; step rows are never a mapped collection (see Run).
 */
public interface RunRepositoryCustom {

  /** Finds a run by its idempotency scope: workflow plus key. */
  Optional<Run> findByWorkflowAndKey(String workflowId, String idempotencyKey);

  /** Finds the workflow's RUNNING or PAUSED run, if any. */
  Optional<Run> findActiveByWorkflow(String workflowId);

  /** Lists runs under the filter, newest first. */
  RunPage findRuns(RunFilter filter, int page, int size);

  /** Returns current steps from the RUN_STEP_CURRENT view, in position order. */
  List<RunStep> findCurrentSteps(String runId);

  /** Returns every step row for the run, ordered by attempt then position. */
  List<RunStep> findStepsByRun(String runId);

  /** Inserts one run row (creation only; later saves are updates). */
  void insertRun(Run run);

  /** Inserts one step row. */
  void insertStep(RunStep step);

  /** Updates one step row, matched by its key. */
  void updateStep(RunStep step);
}
