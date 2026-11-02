package com.visakha.snowrunner.cre.enrich;

import com.visakha.snowrunner.catalog.Phase;
import com.visakha.snowrunner.domain.Dimension;
import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.BasePhaseHandler;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * CRE Phone slice of the enrich phase. The driver CALL owns the work; this class only attaches
 * phase ownership. Constitution stage: non-spine — this phase refines off landed tables.
 */
@Component
public class CrePhoneEnrich extends BasePhaseHandler {
  /** Creates the handler. */
  public CrePhoneEnrich() {
    super(Source.CRE, Phase.ENRICH, Set.of(Dimension.PHN));
  }
}
