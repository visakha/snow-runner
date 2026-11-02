package com.visakha.snowrunner.eci.flush;

import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.BaseLifecycleHook;
import com.visakha.snowrunner.execution.LifecycleRole;
import org.springframework.stereotype.Component;

/** Eci flush hook. No-op today: no flush procedure exists; the MDM batch reads V_MDM_*. */
@Component
public class EciFlush extends BaseLifecycleHook {
  /** Creates the hook. */
  public EciFlush() {
    super(Source.ECI, LifecycleRole.FLUSH);
  }
}
