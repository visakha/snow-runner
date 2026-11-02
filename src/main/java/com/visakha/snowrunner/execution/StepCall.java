package com.visakha.snowrunner.execution;

import com.visakha.snowrunner.catalog.StepDefinition;
import com.visakha.snowrunner.domain.ProcedureName;
import com.visakha.snowrunner.domain.RunTimestamp;
import com.visakha.snowrunner.domain.Source;
import java.util.List;

/**
 * One resolved CALL: everything the caller needs, with parameters already resolved to values for
 * this run. The caller never sees bindings, only values — and values are bound, never concatenated
 * into a CALL string.
 *
 * <p>With today's catalog, arguments is empty: driver steps carry zero parameters and the CALL is
 * zero-arg (defaults fill the drivers' code parameters). Bindings resolve only if a future catalog
 * declares them.
 */
public record StepCall(
    String runId,
    String stepId,
    ProcedureName procedureName,
    RunTimestamp runTimestamp,
    Source source,
    int timeoutMinutes,
    List<Object> arguments) {

  /** Resolves a step's bindings for this run, in catalog order. */
  public static StepCall resolve(
      String runId, StepDefinition step, RunTimestamp runTimestamp, Source source) {
    List<Object> arguments =
        step.parameters().stream()
            .map(
                binding ->
                    switch (binding.binding()) {
                      case "$RUN_TIMESTAMP" ->
                          (Object) java.sql.Timestamp.from(runTimestamp.value());
                      case "$SOURCE" -> source.name();
                      default -> binding.binding();
                    })
            .toList();
    return new StepCall(
        runId,
        step.stepId().value(),
        step.procedureName(),
        runTimestamp,
        source,
        step.timeoutMinutes(),
        arguments);
  }
}
