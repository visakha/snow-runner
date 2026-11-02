package com.visakha.snowrunner.wic.enrich;

import com.visakha.snowrunner.catalog.Phase;
import com.visakha.snowrunner.domain.Dimension;
import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.BasePhaseHandler;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * WIC Party slice of the enrich phase. The driver CALL owns the work; this class only attaches
 * phase ownership. Constitution stage: non-spine — this phase refines off landed tables.
 */
@Component
public class WicPartyEnrich extends BasePhaseHandler {
  /** Creates the handler. */
  public WicPartyEnrich() {
    super(Source.WIC, Phase.ENRICH, Set.of(Dimension.PTY));
  }
}
