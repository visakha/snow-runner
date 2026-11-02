package com.visakha.snowrunner.fss.flush;

import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.BaseLifecycleHook;
import com.visakha.snowrunner.execution.LifecycleRole;
import org.springframework.stereotype.Component;

/** Fss flush hook. No-op today: no flush procedure exists; the MDM batch reads V_MDM_*. */
@Component
public class FssFlush extends BaseLifecycleHook {
  /** Creates the hook. */
  public FssFlush() {
    super(Source.FSS, LifecycleRole.FLUSH);
  }
}
