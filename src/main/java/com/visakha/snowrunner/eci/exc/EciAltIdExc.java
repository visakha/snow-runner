package com.visakha.snowrunner.eci.exc;

import com.visakha.snowrunner.catalog.Phase;
import com.visakha.snowrunner.domain.Dimension;
import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.BasePhaseHandler;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * ECI AltId slice of the exc phase. The driver CALL owns the work; this class only attaches phase
 * ownership. Constitution stage: non-spine — this phase refines off landed tables.
 */
@Component
public class EciAltIdExc extends BasePhaseHandler {
  /** Creates the handler. */
  public EciAltIdExc() {
    super(Source.ECI, Phase.EXC, Set.of(Dimension.ALT_ID));
  }
}
