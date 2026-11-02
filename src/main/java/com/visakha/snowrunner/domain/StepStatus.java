package com.visakha.snowrunner.domain;

/** Step states (005 §1). A step never returns to PENDING within an attempt. */
public enum StepStatus {
  PENDING,
  RUNNING,
  SUCCEEDED,
  FAILED,
  SKIPPED
}
