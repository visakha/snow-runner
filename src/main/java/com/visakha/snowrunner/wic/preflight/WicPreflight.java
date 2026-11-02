package com.visakha.snowrunner.wic.preflight;

import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.BaseLifecycleHook;
import com.visakha.snowrunner.execution.LifecycleRole;
import org.springframework.stereotype.Component;

/** Wic preflight hook. No-op today: preflight itself is PreflightService. */
@Component
public class WicPreflight extends BaseLifecycleHook {
  /** Creates the hook. */
  public WicPreflight() {
    super(Source.WIC, LifecycleRole.PREFLIGHT);
  }
}
