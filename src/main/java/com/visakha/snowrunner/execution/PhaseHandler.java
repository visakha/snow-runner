package com.visakha.snowrunner.execution;

import com.visakha.snowrunner.catalog.Phase;
import com.visakha.snowrunner.domain.Dimension;
import com.visakha.snowrunner.domain.RunStep;
import com.visakha.snowrunner.domain.Source;
import java.util.Set;

/**
 * A phase class: the Java-side owner of one (source, phase, dimension) slice. Phase classes hold no
 * ETL logic and issue no CALLs — the driver CALL owns the work, and the handler only attaches phase
 * ownership for observability. They exist as code, bound by the registry, because naming a class in
 * YAML would need reflection to instantiate, which this project forbids.
 */
public interface PhaseHandler {
  /** Returns the source this handler belongs to. */
  Source source();

  /** Returns the phase this handler owns. */
  Phase phase();

  /** Returns the dimensions this handler covers. */
  Set<Dimension> dimensions();

  /** Hook before the step's CALL. No-op by default; overridden only where a phase needs it. */
  default void beforeStep(StepContext context) {}

  /** Hook after the step's CALL, with the finished row. No-op by default. */
  default void afterStep(StepContext context, RunStep finished) {}
}
