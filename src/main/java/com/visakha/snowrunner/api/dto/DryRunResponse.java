package com.visakha.snowrunner.api.dto;

import com.visakha.snowrunner.execution.DryRunResult;
import java.util.List;

/**
 * Dry-run response. The dryRun flag and the absence of a runId exist so no consumer can mistake
 * this for a run: there is no runId in the response at all, by design.
 */
public record DryRunResponse(
    String workflowId, String catalogVersion, boolean dryRun, List<DryRunStep> steps) {

  /** Maps a dry-run result to its response shape. */
  public static DryRunResponse from(DryRunResult result) {
    List<DryRunStep> steps =
        result.steps().stream()
            .map(
                step ->
                    new DryRunStep(
                        step.stepId(),
                        step.position(),
                        step.procedureName(),
                        step.enabled(),
                        step.rerunSafety().name()))
            .toList();
    return new DryRunResponse(result.workflowId(), result.catalogVersion(), true, steps);
  }

  /** One step as a dry-run lists it. */
  public record DryRunStep(
      String stepId, int position, String procedureName, boolean enabled, String rerunSafety) {}
}
