package com.visakha.snowrunner.cre.loader;

import com.visakha.snowrunner.catalog.Phase;
import com.visakha.snowrunner.domain.Dimension;
import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.BasePhaseHandler;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * CRE Address slice of the loader phase. The driver CALL owns the work; this class only attaches
 * phase ownership. Constitution stage: spine — this phase lands rows on C_S_*.
 */
@Component
public class CreAddressLoader extends BasePhaseHandler {
  /** Creates the handler. */
  public CreAddressLoader() {
    super(Source.CRE, Phase.LOADER, Set.of(Dimension.ADR));
  }
}
