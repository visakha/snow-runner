package com.visakha.snowrunner.eci.loader;

import com.visakha.snowrunner.catalog.Phase;
import com.visakha.snowrunner.domain.Dimension;
import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.BasePhaseHandler;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * ECI Reactivation slice of the loader phase. It spans Address, Phone, and AltId, so it attaches to
 * every step touching any of them. The driver CALL owns the work; this class only attaches phase
 * ownership. Constitution stage: spine — this phase lands rows on C_S_*.
 */
@Component
public class EciReactivationLoader extends BasePhaseHandler {
  /** Creates the handler. */
  public EciReactivationLoader() {
    super(Source.ECI, Phase.LOADER, Set.of(Dimension.ADR, Dimension.PHN, Dimension.ALT_ID));
  }
}
