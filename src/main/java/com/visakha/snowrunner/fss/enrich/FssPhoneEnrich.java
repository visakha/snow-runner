package com.visakha.snowrunner.fss.enrich;

import com.visakha.snowrunner.catalog.Phase;
import com.visakha.snowrunner.domain.Dimension;
import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.BasePhaseHandler;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * FSS Phone slice of the enrich phase. The driver CALL owns the work; this class only attaches
 * phase ownership. Constitution stage: non-spine — this phase refines off landed tables.
 */
@Component
public class FssPhoneEnrich extends BasePhaseHandler {
  /** Creates the handler. */
  public FssPhoneEnrich() {
    super(Source.FSS, Phase.ENRICH, Set.of(Dimension.PHN));
  }
}
