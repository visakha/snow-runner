package com.visakha.snowrunner.api;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.visakha.snowrunner.execution.TestDoubles;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/** One test method per workflow API call. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDoubles.class)
class WorkflowControllerTest extends ApiTestBase {

  @Test
  void dryRunReturnsStepsWithoutCreatingRun() throws Exception {
    mockMvc
        .perform(post("/api/v1/workflows/TRS_ONGOING/dry-run").headers(schedulerHeaders()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workflowId").value("TRS_ONGOING"))
        .andExpect(jsonPath("$.dryRun").value(true))
        .andExpect(jsonPath("$.runId").doesNotExist())
        .andExpect(jsonPath("$.steps", hasSize(3)))
        .andExpect(jsonPath("$.steps[0].procedureName").value("TRS_ETL_WRK.sp_test_pty"));
    assertEquals(0L, repository.count(), "a dry-run must not create a RUN row");
  }

  @Test
  void dryRunUnknownWorkflowReturns404() throws Exception {
    mockMvc
        .perform(post("/api/v1/workflows/NOPE/dry-run").headers(schedulerHeaders()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("WORKFLOW_NOT_FOUND"));
  }

  @Test
  void listWorkflowsReturnsCatalog() throws Exception {
    mockMvc
        .perform(get("/api/v1/workflows").headers(schedulerHeaders()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.catalogVersion").value("0.1-test"))
        .andExpect(jsonPath("$.workflows[0].workflowId").value("TRS_ONGOING"))
        .andExpect(jsonPath("$.workflows[0].steps", hasSize(3)));
  }
}
