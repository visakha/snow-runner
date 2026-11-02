package com.visakha.snowrunner.api.dto;

import com.visakha.snowrunner.catalog.WorkflowDefinition;
import java.util.List;

/** The catalog as the application sees it. */
public record WorkflowResponse(String catalogVersion, List<Workflow> workflows) {

  /** Maps catalog workflows to their response shape. */
  public static WorkflowResponse from(String catalogVersion, List<WorkflowDefinition> definitions) {
    List<Workflow> workflows =
        definitions.stream()
            .map(
                definition ->
                    new Workflow(
                        definition.id(),
                        definition.source().name(),
                        definition.steps().stream()
                            .map(
                                step ->
                                    new WorkflowStep(
                                        step.stepId().value(),
                                        step.position(),
                                        step.procedureName().qualified(),
                                        step.enabled(),
                                        step.rerunSafety().name(),
                                        step.touches().stream().map(Enum::name).sorted().toList()))
                            .toList()))
            .toList();
    return new WorkflowResponse(catalogVersion, workflows);
  }

  /** One workflow with its steps. */
  public record Workflow(String workflowId, String source, List<WorkflowStep> steps) {}

  /** One catalog step as listed. */
  public record WorkflowStep(
      String stepId,
      int position,
      String procedureName,
      boolean enabled,
      String rerunSafety,
      List<String> touches) {}
}
