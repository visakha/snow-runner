package com.visakha.snowrunner.execution;

import com.visakha.snowrunner.catalog.StepDefinition;
import java.util.HashSet;
import java.util.Set;

/** Test double for PreflightCheck: everything exists unless the test says otherwise. */
public class FakePreflightCheck implements PreflightCheck {

  private final Set<String> missing = new HashSet<>();

  /** Marks a step's procedure as missing. */
  public void markMissing(String stepId) {
    missing.add(stepId);
  }

  /** Clears all programmed misses. */
  public void reset() {
    missing.clear();
  }

  @Override
  public boolean procedureExists(StepDefinition step) {
    return !missing.contains(step.stepId().value());
  }
}
