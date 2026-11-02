package com.visakha.snowrunner.execution;

import com.visakha.snowrunner.domain.Clock;
import com.visakha.snowrunner.domain.Run;
import com.visakha.snowrunner.domain.RunStatus;
import com.visakha.snowrunner.domain.RunStep;
import com.visakha.snowrunner.domain.SkipReason;
import com.visakha.snowrunner.domain.StepStatus;
import com.visakha.snowrunner.persistence.RunFilter;
import com.visakha.snowrunner.persistence.RunRepository;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Startup reconciliation (005 §6): a run left RUNNING by a dead process instance is marked FAILED,
 * never silently adopted. Runs after Flyway and before traffic is accepted, so the application is
 * not ready until reconciliation has completed.
 */
@Component
public class StartupReconciliation implements ApplicationRunner {

  static final String STOPPED_REASON = "application stopped mid-run";

  private static final Logger log = LoggerFactory.getLogger(StartupReconciliation.class);

  private final RunRepository repository;
  private final Clock clock;
  private final AtomicBoolean complete = new AtomicBoolean(false);

  /** Creates the reconciliation task. */
  public StartupReconciliation(RunRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  /** Returns whether reconciliation has finished. */
  public boolean complete() {
    return complete.get();
  }

  @Override
  public void run(ApplicationArguments args) {
    List<Run> orphaned =
        repository
            .findRuns(
                new RunFilter(null, null, RunStatus.RUNNING, null, null), 0, Integer.MAX_VALUE)
            .content();
    for (Run run : orphaned) {
      List<RunStep> current = repository.findCurrentSteps(run.runId());
      for (RunStep row : current) {
        if (row.status() == StepStatus.RUNNING) {
          repository.updateStep(
              row.finished(StepStatus.FAILED, row.queryId(), clock.now(), STOPPED_REASON));
        } else if (row.status() == StepStatus.PENDING) {
          repository.updateStep(row.skipped(SkipReason.APP_STOPPED));
        }
      }
      run.status(RunStatus.FAILED);
      run.updatedAt(clock.now());
      run.endedAt(run.updatedAt());
      run.errorMessage(STOPPED_REASON);
      repository.save(run);
      log.info("run {} reconciled as FAILED: {}", run.runId(), STOPPED_REASON);
    }
    complete.set(true);
  }
}
