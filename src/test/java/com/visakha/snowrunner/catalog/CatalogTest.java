package com.visakha.snowrunner.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.visakha.snowrunner.domain.TargetDatabase;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Catalog loading: the real file parses, and bad files fail fast with a location. */
class CatalogTest {

  @Test
  void parsesRealCatalog() {
    InputStream input = getClass().getResourceAsStream("/steps-catalog.yml");
    YamlStepCatalog catalog = YamlStepCatalog.load(input, "steps-catalog.yml");
    assertEquals(TargetDatabase.SNOWFLAKE, catalog.targetDatabase());
    assertEquals(7, catalog.workflows().size());
    int steps = catalog.workflows().stream().mapToInt(w -> w.steps().size()).sum();
    assertEquals(40, steps);
    WorkflowDefinition trs = catalog.workflow("TRS_ONGOING").orElseThrow();
    assertEquals(
        "TRS_ETL_WRK.sp_snow_main_m_Tgt_C_S_TRS_Pty_OnGoing",
        trs.steps().get(0).procedureName().qualified());
    WorkflowDefinition cre = catalog.workflow("CRE_STAGE_LOAD_ONGOING").orElseThrow();
    assertEquals(45, cre.steps().get(0).timeoutMinutes());
  }

  @Test
  void rejectsUnknownTargetDatabase() {
    String yaml =
        """
        catalogVersion: 0.1-test
        targetDatabase: postgres
        schemas:
          snowflake:
            TRS: TRS_ETL_WRK
        workflows: []
        """;
    assertThrows(CatalogException.class, () -> load(yaml));
  }

  @Test
  void rejectsDuplicateStepId() {
    String yaml =
        """
        catalogVersion: 0.1-test
        targetDatabase: snowflake
        schemas:
          snowflake:
            TRS: TRS_ETL_WRK
        defaults:
          parameters:
            P_RUN_TS: $RUN_TIMESTAMP
            P_SOURCE: $SOURCE
        workflows:
          - id: TRS_ONGOING
            source: TRS
            steps:
              - id: STEP_A
                procedure: sp_a
                oracleProcedure: DUMMY_ORA_STEP_A
                enabled: true
                phases: [loader]
                touches: [PTY]
                rerunSafety: SAFE
              - id: STEP_A
                procedure: sp_b
                oracleProcedure: DUMMY_ORA_STEP_B
                enabled: true
                phases: [loader]
                touches: [PTY]
                rerunSafety: SAFE
        """;
    assertThrows(CatalogException.class, () -> load(yaml));
  }

  @Test
  void oracleCatalogResolvesOracleProcedures() {
    String yaml =
        """
        catalogVersion: 0.1-test
        targetDatabase: oracle
        schemas:
          oracle:
            TRS: DUMMY_ORA_TRS_WRK
        defaults:
          parameters:
            P_RUN_TS: $RUN_TIMESTAMP
            P_SOURCE: $SOURCE
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
        """;
    YamlStepCatalog catalog = load(yaml);
    assertEquals(TargetDatabase.ORACLE, catalog.targetDatabase());
    assertEquals(
        "DUMMY_ORA_TRS_WRK.ORA_STEP_A",
        catalog.workflow("TRS_ONGOING").orElseThrow().steps().get(0).procedureName().qualified());
  }

  @Test
  void driverStepsTakeZeroArguments() {
    // Cross-check finding 1: drivers take only defaulted code parameters and generate
    // their own run timestamp, so the catalog must bind no arguments to driver CALLs.
    // Binding (run timestamp, source) positionally would misbind code parameters.
    InputStream input = getClass().getResourceAsStream("/steps-catalog.yml");
    YamlStepCatalog catalog = YamlStepCatalog.load(input, "steps-catalog.yml");
    catalog
        .workflows()
        .forEach(
            workflow ->
                workflow
                    .steps()
                    .forEach(
                        step -> assertTrue(step.parameters().isEmpty(), step.stepId().value())));
  }

  @Test
  void realCatalogOracleSideResolvesMirroredNames() throws Exception {
    // Cross-check wiring: with the flag flipped to oracle, every step must resolve to
    // the mirrored procedure in its same-named schema — never a DUMMY placeholder.
    String yaml;
    try (InputStream input = getClass().getResourceAsStream("/steps-catalog.yml")) {
      yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
    yaml = yaml.replace("targetDatabase: snowflake", "targetDatabase: oracle");
    YamlStepCatalog catalog = load(yaml);
    assertEquals(TargetDatabase.ORACLE, catalog.targetDatabase());
    int steps =
        catalog.workflows().stream()
            .flatMap(workflow -> workflow.steps().stream())
            .mapToInt(
                step -> {
                  String qualified = step.procedureName().qualified();
                  String[] parts = qualified.split("\\.", 2);
                  assertEquals(2, parts.length, qualified);
                  assertTrue(!qualified.contains("DUMMY"), qualified);
                  assertTrue(parts[0].endsWith("_ETL_WRK"), qualified);
                  return 1;
                })
            .sum();
    assertEquals(40, steps);
    assertEquals(
        "TRS_ETL_WRK.sp_snow_main_m_Tgt_C_S_TRS_Pty_OnGoing",
        catalog.workflow("TRS_ONGOING").orElseThrow().steps().get(0).procedureName().qualified());
  }

  private static YamlStepCatalog load(String yaml) {
    return YamlStepCatalog.load(
        new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test.yml");
  }
}
