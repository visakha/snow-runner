package com.visakha.snowrunner.wic.postflight;

import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.BaseLifecycleHook;
import com.visakha.snowrunner.execution.LifecycleRole;
import org.springframework.stereotype.Component;

/** Wic postflight hook. No-op today: closing work lives in the executor. */
@Component
public class WicPostflight extends BaseLifecycleHook {
  /** Creates the hook. */
  public WicPostflight() {
    super(Source.WIC, LifecycleRole.POSTFLIGHT);
  }
}
