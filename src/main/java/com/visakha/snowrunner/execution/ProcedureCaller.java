package com.visakha.snowrunner.execution;

/**
 * Issues exactly one bound CALL. One implementation per database, chosen once at startup by the
 * catalog's targetDatabase flag — never per step. The caller knows nothing about workflows; it
 * knows a procedure name and bound arguments.
 */
public interface ProcedureCaller {
  /**
   * Issues the CALL and returns its outcome, or throws ProcedureCallException with the database's
   * verbatim error.
   */
  CallResult call(StepCall call);
}
