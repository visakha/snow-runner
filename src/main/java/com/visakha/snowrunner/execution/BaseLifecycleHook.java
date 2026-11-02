package com.visakha.snowrunner.execution;

import com.visakha.snowrunner.domain.Source;

/** Base for the generated lifecycle classes: just the (source, role) pair. */
public abstract class BaseLifecycleHook implements LifecycleHook {

  private final Source source;
  private final LifecycleRole role;

  /** Creates the hook for one lifecycle operation of one source. */
  protected BaseLifecycleHook(Source source, LifecycleRole role) {
    this.source = source;
    this.role = role;
  }

  @Override
  public Source source() {
    return source;
  }

  @Override
  public LifecycleRole role() {
    return role;
  }
}
