package com.visakha.snowrunner.trs.preflight;

import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.BaseLifecycleHook;
import com.visakha.snowrunner.execution.LifecycleRole;
import org.springframework.stereotype.Component;

/** Trs preflight hook. No-op today: preflight itself is PreflightService. */
@Component
public class TrsPreflight extends BaseLifecycleHook {
  /** Creates the hook. */
  public TrsPreflight() {
    super(Source.TRS, LifecycleRole.PREFLIGHT);
  }
}
