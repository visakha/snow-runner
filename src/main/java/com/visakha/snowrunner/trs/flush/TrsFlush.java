package com.visakha.snowrunner.trs.flush;

import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.BaseLifecycleHook;
import com.visakha.snowrunner.execution.LifecycleRole;
import org.springframework.stereotype.Component;

/** Trs flush hook. No-op today: no flush procedure exists; the MDM batch reads V_MDM_*. */
@Component
public class TrsFlush extends BaseLifecycleHook {
  /** Creates the hook. */
  public TrsFlush() {
    super(Source.TRS, LifecycleRole.FLUSH);
  }
}
