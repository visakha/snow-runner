package com.visakha.snowrunner.domain;

/**
 * Run states (005 §1). No QUEUED in v0.1: there is no queue, a trigger starts execution. QUEUED
 * stays reserved for a future version that queues work.
 */
public enum RunStatus {
  RUNNING,
  SUCCEEDED,
  FAILED,
  PAUSED,
  CANCELLED
}
