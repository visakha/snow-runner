package com.visakha.snowrunner.catalog;

/**
 * One CALL parameter: its procedure-parameter name and its binding. A binding is $RUN_TIMESTAMP
 * (this run's timestamp), $SOURCE (the workflow's source), or a literal fixed value. Bindings
 * resolve per run, never at load — resolving early would bake one run's timestamp into every future
 * run.
 */
public record ParameterBinding(String name, String binding) {}
