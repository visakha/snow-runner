package com.visakha.snowrunner.catalog;

import com.visakha.snowrunner.domain.Source;
import java.util.List;

/** One workflow: an ordered, immutable list of steps for one source. */
public record WorkflowDefinition(String id, Source source, List<StepDefinition> steps) {}
