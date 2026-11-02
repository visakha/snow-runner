package com.visakha.snowrunner.catalog;

import com.visakha.snowrunner.domain.TargetDatabase;
import java.util.List;
import java.util.Optional;

/**
 * The loaded catalog, behind an interface. The executor never reads the YAML file; it asks here.
 * One bean, immutable, for the life of the application.
 */
public interface StepCatalog {
  /** Returns the catalog version string from the file. */
  String catalogVersion();

  /** Returns the database the whole catalog runs against. */
  TargetDatabase targetDatabase();

  /** Returns all workflows in file order. */
  List<WorkflowDefinition> workflows();

  /** Returns the workflow with this id, or empty. */
  Optional<WorkflowDefinition> workflow(String workflowId);

  /** Returns the workflow's steps in execution order, or empty if unknown. */
  default Optional<List<StepDefinition>> stepsFor(String workflowId) {
    return workflow(workflowId).map(WorkflowDefinition::steps);
  }
}
