package com.visakha.snowrunner.execution;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Readiness gate for reconciliation: the application is not ready to accept triggers until the
 * previous process's orphaned runs have been reconciled. Liveness is unaffected — the process is up
 * while it reconciles.
 */
@Component
public class ReconciliationHealthIndicator implements HealthIndicator {

  private final StartupReconciliation reconciliation;

  /** Creates the indicator around the reconciliation task. */
  public ReconciliationHealthIndicator(StartupReconciliation reconciliation) {
    this.reconciliation = reconciliation;
  }

  @Override
  public Health health() {
    if (reconciliation.complete()) {
      return Health.up().build();
    }
    return Health.outOfService().withDetail("reason", "reconciliation incomplete").build();
  }
}
