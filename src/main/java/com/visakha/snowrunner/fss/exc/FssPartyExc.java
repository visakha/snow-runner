package com.visakha.snowrunner.fss.exc;

import com.visakha.snowrunner.catalog.Phase;
import com.visakha.snowrunner.domain.Dimension;
import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.BasePhaseHandler;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * FSS Party slice of the exc phase. The driver CALL owns the work; this class only attaches phase
 * ownership. Constitution stage: non-spine — this phase refines off landed tables.
 */
@Component
public class FssPartyExc extends BasePhaseHandler {
  /** Creates the handler. */
  public FssPartyExc() {
    super(Source.FSS, Phase.EXC, Set.of(Dimension.PTY));
  }
}
