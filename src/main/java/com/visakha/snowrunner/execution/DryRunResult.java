package com.visakha.snowrunner.execution;

import java.util.List;

/** A dry-run's answer: the ordered steps with resolved names. No run row, no CALL. */
public record DryRunResult(String workflowId, String catalogVersion, List<DryStep> steps) {}
