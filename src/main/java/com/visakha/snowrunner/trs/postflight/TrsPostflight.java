package com.visakha.snowrunner.trs.postflight;

import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.BaseLifecycleHook;
import com.visakha.snowrunner.execution.LifecycleRole;
import org.springframework.stereotype.Component;

/** Trs postflight hook. No-op today: closing work lives in the executor. */
@Component
public class TrsPostflight extends BaseLifecycleHook {
  /** Creates the hook. */
  public TrsPostflight() {
    super(Source.TRS, LifecycleRole.POSTFLIGHT);
  }
}
