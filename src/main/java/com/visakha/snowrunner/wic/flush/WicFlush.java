package com.visakha.snowrunner.wic.flush;

import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.BaseLifecycleHook;
import com.visakha.snowrunner.execution.LifecycleRole;
import org.springframework.stereotype.Component;

/** Wic flush hook. No-op today: no flush procedure exists; the MDM batch reads V_MDM_*. */
@Component
public class WicFlush extends BaseLifecycleHook {
  /** Creates the hook. */
  public WicFlush() {
    super(Source.WIC, LifecycleRole.FLUSH);
  }
}
