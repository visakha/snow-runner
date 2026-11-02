package com.visakha.snowrunner.execution;

import com.visakha.snowrunner.domain.RerunSafety;

/** One step as a dry-run lists it: identity and resolved name, nothing executed. */
public record DryStep(
    String stepId, int position, String procedureName, boolean enabled, RerunSafety rerunSafety) {}
