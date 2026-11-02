package com.visakha.snowrunner.execution;

import com.visakha.snowrunner.catalog.Phase;
import com.visakha.snowrunner.domain.Dimension;
import com.visakha.snowrunner.domain.Source;
import java.util.Set;

/** Base for the generated phase classes: just the (source, phase, dimensions) triple. */
public abstract class BasePhaseHandler implements PhaseHandler {

  private final Source source;
  private final Phase phase;
  private final Set<Dimension> dimensions;

  /** Creates the handler for one slice. */
  protected BasePhaseHandler(Source source, Phase phase, Set<Dimension> dimensions) {
    this.source = source;
    this.phase = phase;
    this.dimensions = Set.copyOf(dimensions);
  }

  @Override
  public Source source() {
    return source;
  }

  @Override
  public Phase phase() {
    return phase;
  }

  @Override
  public Set<Dimension> dimensions() {
    return dimensions;
  }
}
