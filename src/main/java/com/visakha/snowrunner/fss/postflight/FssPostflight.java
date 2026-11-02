package com.visakha.snowrunner.fss.postflight;

import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.BaseLifecycleHook;
import com.visakha.snowrunner.execution.LifecycleRole;
import org.springframework.stereotype.Component;

/** Fss postflight hook. No-op today: closing work lives in the executor. */
@Component
public class FssPostflight extends BaseLifecycleHook {
  /** Creates the hook. */
  public FssPostflight() {
    super(Source.FSS, LifecycleRole.POSTFLIGHT);
  }
}
