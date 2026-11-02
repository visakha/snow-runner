package com.visakha.snowrunner.infra.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.visakha.snowrunner.catalog.YamlStepCatalog;
import com.visakha.snowrunner.infra.oracle.OracleProcedureCaller;
import com.visakha.snowrunner.infra.snowflake.SnowflakeProcedureCaller;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** The targetDatabase flag selects the caller once at startup, never per step. */
class CallerSelectionTest {

  @Test
  void selectsCallerByFlag() {
    var configuration = new CallerConfiguration();
    var jdbc = new JdbcTemplate();
    assertInstanceOf(
        SnowflakeProcedureCaller.class, configuration.procedureCaller(load("snowflake"), jdbc));
    assertInstanceOf(
        OracleProcedureCaller.class, configuration.procedureCaller(load("oracle"), jdbc));
  }

  private static YamlStepCatalog load(String target) {
    String yaml =
        """
        catalogVersion: 0.1-test
        targetDatabase: %s
        schemas:
          snowflake:
            TRS: TRS_ETL_WRK
          oracle:
            TRS: DUMMY_ORA_TRS_WRK
        workflows:
          - id: TRS_ONGOING
            source: TRS
            steps:
              - id: STEP_A
                procedure: sp_a
                oracleProcedure: ORA_STEP_A
                enabled: true
                phases: [loader]
                touches: [PTY]
                rerunSafety: SAFE
        """
            .formatted(target);
    return YamlStepCatalog.load(
        new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test.yml");
  }
}
