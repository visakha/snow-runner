package com.visakha.snowrunner.eci.dqm;

import com.visakha.snowrunner.catalog.Phase;
import com.visakha.snowrunner.domain.Dimension;
import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.BasePhaseHandler;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * ECI Address slice of the dqm phase. The driver CALL owns the work; this class only attaches phase
 * ownership. Constitution stage: non-spine — this phase refines off landed tables.
 */
@Component
public class EciAddressDqm extends BasePhaseHandler {
  /** Creates the handler. */
  public EciAddressDqm() {
    super(Source.ECI, Phase.DQM, Set.of(Dimension.ADR));
  }
}
