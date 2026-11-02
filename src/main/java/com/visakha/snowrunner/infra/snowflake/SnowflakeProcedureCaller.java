package com.visakha.snowrunner.infra.snowflake;

import com.visakha.snowrunner.execution.CallResult;
import com.visakha.snowrunner.execution.ProcedureCallException;
import com.visakha.snowrunner.execution.ProcedureCaller;
import com.visakha.snowrunner.execution.StepCall;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.snowflake.client.api.statement.SnowflakeStatement;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Snowflake CALLs: bound parameters only, on one connection per CALL so the QUERY_TAG set first
 * applies to the CALL that follows. The tag joins app telemetry to QUERY_HISTORY by run and step.
 * Tag values are safe to inline: run ids are UUIDs and step ids are validated at catalog load to
 * uppercase letters, digits, and underscores.
 *
 * <p>The caller executes driver CALLs only. It never reads C_S_* or V_* tables: landed data is the
 * procedures' business, and the views are the downstream interface, not Java's.
 */
public class SnowflakeProcedureCaller implements ProcedureCaller {

  private final JdbcTemplate etlJdbc;

  /** Creates the caller around the ETL connection. */
  public SnowflakeProcedureCaller(JdbcTemplate etlJdbc) {
    this.etlJdbc = etlJdbc;
  }

  @Override
  public CallResult call(StepCall call) {
    long start = System.nanoTime();
    // Explicit Connection type: without it the lambda is ambiguous between
    // ConnectionCallback and StatementCallback.
    String queryId =
        etlJdbc.execute(
            (Connection connection) -> {
              String tag = "snow-runner:" + call.runId() + ":" + call.stepId();
              try (Statement tagStatement = connection.createStatement()) {
                tagStatement.execute("ALTER SESSION SET QUERY_TAG = '" + tag + "'");
              } catch (SQLException e) {
                throw new ProcedureCallException("Failed to set QUERY_TAG: " + e.getMessage());
              }
              String placeholders =
                  IntStream.range(0, call.arguments().size())
                      .mapToObj(index -> "?")
                      .collect(Collectors.joining(", "));
              String sql = "CALL " + call.procedureName().qualified() + "(" + placeholders + ")";
              try (CallableStatement statement = connection.prepareCall(sql)) {
                for (int index = 0; index < call.arguments().size(); index++) {
                  statement.setObject(index + 1, call.arguments().get(index));
                }
                statement.setQueryTimeout(call.timeoutMinutes() * 60);
                statement.execute();
                return readQueryId(statement);
              } catch (SQLException e) {
                throw new ProcedureCallException(e.getMessage());
              }
            });
    long durationMs = (System.nanoTime() - start) / 1_000_000;
    return new CallResult(queryId, durationMs);
  }

  private static String readQueryId(CallableStatement statement) {
    try {
      return statement.unwrap(SnowflakeStatement.class).getQueryID();
    } catch (SQLException e) {
      return null;
    }
  }
}
