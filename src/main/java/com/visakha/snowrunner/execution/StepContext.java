package com.visakha.snowrunner.execution;

import com.visakha.snowrunner.catalog.StepDefinition;
import com.visakha.snowrunner.domain.Run;

/** What a phase handler sees: the run, the step, and the attempt under way. */
public record StepContext(Run run, StepDefinition step, int attemptNo) {}
