package com.visakha.snowrunner.ttf.loader;

import com.visakha.snowrunner.catalog.Phase;
import com.visakha.snowrunner.domain.Dimension;
import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.execution.BasePhaseHandler;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * TTF Phone slice of the loader phase. The driver CALL owns the work; this class only attaches
 * phase ownership. Constitution stage: spine — this phase lands rows on C_S_*.
 */
@Component
public class TtfPhoneLoader extends BasePhaseHandler {
  /** Creates the handler. */
  public TtfPhoneLoader() {
    super(Source.TTF, Phase.LOADER, Set.of(Dimension.PHN));
  }
}
