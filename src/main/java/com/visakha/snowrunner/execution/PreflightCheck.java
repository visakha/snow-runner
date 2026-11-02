package com.visakha.snowrunner.execution;

import com.visakha.snowrunner.catalog.StepDefinition;

/** One read-only preflight check. v0.1 checks existence only, never signatures. */
public interface PreflightCheck {
  /** Returns whether the step's procedure exists in its schema. */
  boolean procedureExists(StepDefinition step);
}
