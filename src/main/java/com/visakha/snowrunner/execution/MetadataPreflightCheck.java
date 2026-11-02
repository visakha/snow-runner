package com.visakha.snowrunner.execution;

import com.visakha.snowrunner.catalog.StepDefinition;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Existence check through DatabaseMetaData: read-only, no CALL issued. Names are matched exactly
 * (case-insensitively) against the returned rows, because getProcedures treats its arguments as
 * LIKE patterns — passing a name with underscores straight through would let '_' match any
 * character and could pass preflight for a procedure that does not exist.
 */
@Component
public class MetadataPreflightCheck implements PreflightCheck {

  private final JdbcTemplate etlJdbc;

  /** Uses the ETL connection: preflight asks the target database, not the control store. */
  public MetadataPreflightCheck(@Qualifier("etlJdbcTemplate") JdbcTemplate etlJdbc) {
    this.etlJdbc = etlJdbc;
  }

  @Override
  public boolean procedureExists(StepDefinition step) {
    String schema = step.procedureName().schema();
    String name = step.procedureName().name();
    // Explicit Connection type: without it the lambda is ambiguous between
    // ConnectionCallback and StatementCallback.
    Boolean found =
        etlJdbc.execute(
            (Connection connection) -> {
              DatabaseMetaData meta = connection.getMetaData();
              try (ResultSet rows = meta.getProcedures(null, schema, name)) {
                while (rows.next()) {
                  if (schema.equalsIgnoreCase(rows.getString("PROCEDURE_SCHEM"))
                      && name.equalsIgnoreCase(rows.getString("PROCEDURE_NAME"))) {
                    return true;
                  }
                }
                return false;
              }
            });
    return Boolean.TRUE.equals(found);
  }
}
