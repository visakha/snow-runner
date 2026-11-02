package com.visakha.snowrunner.execution;

/**
 * Lifecycle operations. They are not catalog steps and have no procedure: preflight is read-only
 * existence checks, flush has no procedure in the estate today, postflight is closing work.
 */
public enum LifecycleRole {
  PREFLIGHT,
  FLUSH,
  POSTFLIGHT
}
