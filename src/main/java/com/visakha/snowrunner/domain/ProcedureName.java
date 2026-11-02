package com.visakha.snowrunner.domain;

/**
 * A resolved procedure identity: schema plus name, from the catalog only, never from user input.
 * The schema is never concatenated into a CALL string at the call site beyond this validated value.
 */
public record ProcedureName(String schema, String name) {
  /** Returns the schema-qualified name used in the CALL. */
  public String qualified() {
    return schema + "." + name;
  }
}
