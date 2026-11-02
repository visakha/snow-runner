package com.visakha.snowrunner.domain;

import java.time.Instant;

/**
 * Time source, so tests can fix time instead of racing it. Production uses SystemClock; tests use a
 * fixed clock.
 */
public interface Clock {
  /** Returns the current instant. */
  Instant now();
}
