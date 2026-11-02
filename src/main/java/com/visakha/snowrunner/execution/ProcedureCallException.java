package com.visakha.snowrunner.execution;

/**
 * A failed CALL, carrying the database's verbatim error. The executor records the message on the
 * step row as-is: the operator sees what the database said, not a paraphrase.
 */
public class ProcedureCallException extends RuntimeException {

  private final String queryId;

  /** Creates the exception with the verbatim error and the query id, if one was issued. */
  public ProcedureCallException(String message, String queryId) {
    super(message);
    this.queryId = queryId;
  }

  /** Creates the exception with the verbatim error and no query id. */
  public ProcedureCallException(String message) {
    this(message, null);
  }

  public String queryId() {
    return queryId;
  }
}
