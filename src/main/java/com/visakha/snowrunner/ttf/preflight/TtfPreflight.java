package com.visakha.snowrunner.ttf.preflight;

import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.BaseLifecycleHook;
import com.visakha.snowrunner.execution.LifecycleRole;
import org.springframework.stereotype.Component;

/** Ttf preflight hook. No-op today: preflight itself is PreflightService. */
@Component
public class TtfPreflight extends BaseLifecycleHook {
  /** Creates the hook. */
  public TtfPreflight() {
    super(Source.TTF, LifecycleRole.PREFLIGHT);
  }
}
