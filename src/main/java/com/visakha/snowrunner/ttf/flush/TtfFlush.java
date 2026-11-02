package com.visakha.snowrunner.ttf.flush;

import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.BaseLifecycleHook;
import com.visakha.snowrunner.execution.LifecycleRole;
import org.springframework.stereotype.Component;

/** Ttf flush hook. No-op today: no flush procedure exists; the MDM batch reads V_MDM_*. */
@Component
public class TtfFlush extends BaseLifecycleHook {
  /** Creates the hook. */
  public TtfFlush() {
    super(Source.TTF, LifecycleRole.FLUSH);
  }
}
