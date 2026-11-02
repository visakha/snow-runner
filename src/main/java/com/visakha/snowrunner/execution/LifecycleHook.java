package com.visakha.snowrunner.execution;

import com.visakha.snowrunner.domain.Run;
import com.visakha.snowrunner.domain.Source;

/**
 * A per-source lifecycle class. Hooks are no-ops today by design: preflight itself is
 * PreflightService, and a flush that wrote C_S_* would be new ETL logic — the MDM batch reads
 * V_MDM_*, so there is nothing for a Java flush to do yet. The classes exist so the lifecycle has
 * an explicit, per-source home when behavior is needed.
 */
public interface LifecycleHook {
  /** Returns the source this hook belongs to. */
  Source source();

  /** Returns which lifecycle operation this hook is. */
  LifecycleRole role();

  /** Runs before the first CALL of a run. No-op by default. */
  default void preflight(Run run) {}

  /** Runs after a run succeeds, before it is marked ended. No-op by default. */
  default void flush(Run run) {}

  /** Runs after a run ends, whatever the outcome. No-op by default. */
  default void postflight(Run run) {}
}
