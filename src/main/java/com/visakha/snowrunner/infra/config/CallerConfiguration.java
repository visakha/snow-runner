package com.visakha.snowrunner.infra.config;

import com.visakha.snowrunner.catalog.StepCatalog;
import com.visakha.snowrunner.execution.ProcedureCaller;
import com.visakha.snowrunner.infra.oracle.OracleProcedureCaller;
import com.visakha.snowrunner.infra.snowflake.SnowflakeProcedureCaller;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Selects the one ProcedureCaller for the catalog's targetDatabase flag, once at startup. No step
 * ever branches on the database: by the time a CALL is issued, the choice is made.
 */
@Configuration
public class CallerConfiguration {

  /** Selects the caller for the selected database. */
  @Bean
  public ProcedureCaller procedureCaller(
      StepCatalog catalog, @Qualifier("etlJdbcTemplate") JdbcTemplate etlJdbc) {
    return switch (catalog.targetDatabase()) {
      case SNOWFLAKE -> new SnowflakeProcedureCaller(etlJdbc);
      case ORACLE -> new OracleProcedureCaller(etlJdbc);
    };
  }
}
