package com.visakha.snowrunner.fss.preflight;

import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.BaseLifecycleHook;
import com.visakha.snowrunner.execution.LifecycleRole;
import org.springframework.stereotype.Component;

/** Fss preflight hook. No-op today: preflight itself is PreflightService. */
@Component
public class FssPreflight extends BaseLifecycleHook {
  /** Creates the hook. */
  public FssPreflight() {
    super(Source.FSS, LifecycleRole.PREFLIGHT);
  }
}
