package com.visakha.snowrunner.execution;

import com.visakha.snowrunner.domain.Clock;
import java.time.Instant;

/** Test clock pinned to one instant, so timestamps are assertions, not races. */
public class FixedClock implements Clock {

  private final Instant fixed;

  /** Pins the clock to this instant. */
  public FixedClock(Instant fixed) {
    this.fixed = fixed;
  }

  @Override
  public Instant now() {
    return fixed;
  }
}
