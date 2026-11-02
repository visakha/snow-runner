package com.visakha.snowrunner.wic.valexc;

import com.visakha.snowrunner.catalog.Phase;
import com.visakha.snowrunner.domain.Dimension;
import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.BasePhaseHandler;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * WIC validation slice of the valexc phase. The driver CALL owns the work; this class only attaches
 * phase ownership. Constitution stage: validation gate — it runs first in Validation drivers and
 * maintains the inactive list.
 */
@Component
public class WicValExc extends BasePhaseHandler {
  /** Creates the handler. */
  public WicValExc() {
    super(Source.WIC, Phase.VALEXC, Set.of(Dimension.PTY));
  }
}
