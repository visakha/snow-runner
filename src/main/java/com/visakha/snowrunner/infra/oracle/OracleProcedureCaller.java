package com.visakha.snowrunner.infra.oracle;

import com.visakha.snowrunner.execution.CallResult;
import com.visakha.snowrunner.execution.ProcedureCallException;
import com.visakha.snowrunner.execution.ProcedureCaller;
import com.visakha.snowrunner.execution.StepCall;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Oracle CALLs: bound parameters only, on one connection per CALL so the client info set first
 * applies to the CALL that follows. Oracle exposes no query id like Snowflake's, so the result
 * carries null there; correlation uses the client info string instead.
 *
 * <p>The caller executes driver CALLs only. It never reads C_S_* or V_* tables: landed data is the
 * procedures' business, and the views are the downstream interface, not Java's.
 */
public class OracleProcedureCaller implements ProcedureCaller {

  private final JdbcTemplate etlJdbc;

  /** Creates the caller around the ETL connection. */
  public OracleProcedureCaller(JdbcTemplate etlJdbc) {
    this.etlJdbc = etlJdbc;
  }

  @Override
  public CallResult call(StepCall call) {
    long start = System.nanoTime();
    // Explicit Connection type: without it the lambda is ambiguous between
    // ConnectionCallback and StatementCallback.
    etlJdbc.execute(
        (Connection connection) -> {
          String clientInfo = "snow-runner:" + call.runId() + ":" + call.stepId();
          try (CallableStatement info =
              connection.prepareCall("{call DBMS_APPLICATION_INFO.SET_CLIENT_INFO(?)}")) {
            info.setString(1, clientInfo);
            info.execute();
          } catch (SQLException e) {
            throw new ProcedureCallException("Failed to set client info: " + e.getMessage());
          }
          String placeholders =
              IntStream.range(0, call.arguments().size())
                  .mapToObj(index -> "?")
                  .collect(Collectors.joining(", "));
          String sql = "{call " + call.procedureName().qualified() + "(" + placeholders + ")}";
          try (CallableStatement statement = connection.prepareCall(sql)) {
            for (int index = 0; index < call.arguments().size(); index++) {
              statement.setObject(index + 1, call.arguments().get(index));
            }
            statement.setQueryTimeout(call.timeoutMinutes() * 60);
            statement.execute();
            return null;
          } catch (SQLException e) {
            throw new ProcedureCallException(e.getMessage());
          }
        });
    long durationMs = (System.nanoTime() - start) / 1_000_000;
    return new CallResult(null, durationMs);
  }
}
