package com.visakha.snowrunner.cre.preflight;

import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.BaseLifecycleHook;
import com.visakha.snowrunner.execution.LifecycleRole;
import org.springframework.stereotype.Component;

/** Cre preflight hook. No-op today: preflight itself is PreflightService. */
@Component
public class CrePreflight extends BaseLifecycleHook {
  /** Creates the hook. */
  public CrePreflight() {
    super(Source.CRE, LifecycleRole.PREFLIGHT);
  }
}
