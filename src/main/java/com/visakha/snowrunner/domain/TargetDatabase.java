package com.visakha.snowrunner.domain;

/**
 * Which database the whole catalog runs against. Selected once at startup from the catalog's
 * targetDatabase flag; a run never changes database halfway through.
 *
 * <p>The flag selects caller, schemas, and procedure names — necessary, not sufficient: the Oracle
 * side is the translated mirror tree, statically verified only until a live Oracle compile and stub
 * CALL exist. Switching is edit-flag plus restart, never mid-run.
 */
public enum TargetDatabase {
  SNOWFLAKE,
  ORACLE
}
