package com.visakha.snowrunner.execution;

import com.visakha.snowrunner.catalog.StepDefinition;
import com.visakha.snowrunner.domain.RunStep;
import com.visakha.snowrunner.domain.SkipReason;
import com.visakha.snowrunner.domain.StepStatus;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The whole resume rule (005 §3): execute every step whose current status is not SUCCEEDED and not
 * SKIPPED-disabled. That covers PENDING steps, FAILED steps, and circumstantial skips (FAIL_FAST,
 * APP_STOPPED) — all of which never ran.
 *
 * <p>Why SUCCEEDED steps are never re-executed, even though re-running most of them would be
 * harmless: the DQM crash window. If the application dies after a DQM CALL commits but before its
 * step row is persisted, reconciliation marks that step FAILED and a resume re-executes it,
 * appending a second DQM row — the estate's known DQM-append limitation, narrowed to this one
 * window. It is diagnosable, not preventable in v0.1: the committed CALL is findable in Snowflake's
 * query history by the run's query tag even though the step row has no query id. No automatic dedup
 * is built in.
 */
@Component
public class ResumePolicy {

  /** Selects the catalog steps a resume must execute, in catalog order. */
  public List<StepDefinition> stepsToRun(
      List<RunStep> currentSteps, List<StepDefinition> catalogSteps) {
    Map<String, RunStep> current =
        currentSteps.stream()
            .collect(Collectors.toMap(RunStep::stepId, step -> step, (left, right) -> left));
    return catalogSteps.stream()
        .filter(StepDefinition::enabled)
        .filter(
            step -> {
              RunStep row = current.get(step.stepId().value());
              if (row == null) {
                return true;
              }
              return row.status() != StepStatus.SUCCEEDED
                  && !(row.status() == StepStatus.SKIPPED
                      && row.skippedReason() == SkipReason.DISABLED);
            })
        .toList();
  }
}
