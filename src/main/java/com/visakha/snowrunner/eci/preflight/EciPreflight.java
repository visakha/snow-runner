package com.visakha.snowrunner.eci.preflight;

import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.BaseLifecycleHook;
import com.visakha.snowrunner.execution.LifecycleRole;
import org.springframework.stereotype.Component;

/** Eci preflight hook. No-op today: preflight itself is PreflightService. */
@Component
public class EciPreflight extends BaseLifecycleHook {
  /** Creates the hook. */
  public EciPreflight() {
    super(Source.ECI, LifecycleRole.PREFLIGHT);
  }
}
