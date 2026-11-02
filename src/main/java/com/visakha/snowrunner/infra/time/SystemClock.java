package com.visakha.snowrunner.infra.time;

import com.visakha.snowrunner.domain.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Production clock: the real wall clock. */
@Component
public class SystemClock implements Clock {

  @Override
  public Instant now() {
    return Instant.now();
  }
}
