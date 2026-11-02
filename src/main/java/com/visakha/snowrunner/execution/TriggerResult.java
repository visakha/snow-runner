package com.visakha.snowrunner.execution;

import com.visakha.snowrunner.domain.Run;

/** A trigger's outcome: the run, and whether it was newly created (202) or a retry (200). */
public record TriggerResult(Run run, boolean created) {}
