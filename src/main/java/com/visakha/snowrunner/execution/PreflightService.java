package com.visakha.snowrunner.execution;

import com.visakha.snowrunner.catalog.StepDefinition;
import java.util.List;
import org.springframework.stereotype.Component;

/** Runs the preflight checks before the first CALL of an attempt. */
@Component
public class PreflightService {

  private final PreflightCheck check;

  /** Creates the service around one check. */
  public PreflightService(PreflightCheck check) {
    this.check = check;
  }

  /** Returns the steps whose procedure does not exist — empty means preflight passed. */
  public List<StepDefinition> missingSteps(List<StepDefinition> steps) {
    return steps.stream().filter(step -> !check.procedureExists(step)).toList();
  }
}
