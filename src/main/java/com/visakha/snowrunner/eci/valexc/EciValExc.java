package com.visakha.snowrunner.eci.valexc;

import com.visakha.snowrunner.catalog.Phase;
import com.visakha.snowrunner.domain.Dimension;
import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.BasePhaseHandler;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * ECI validation slice of the valexc phase. The driver CALL owns the work; this class only attaches
 * phase ownership. Constitution stage: validation gate — it runs first in Validation drivers and
 * maintains the inactive list.
 */
@Component
public class EciValExc extends BasePhaseHandler {
  /** Creates the handler. */
  public EciValExc() {
    super(Source.ECI, Phase.VALEXC, Set.of(Dimension.PTY));
  }
}
