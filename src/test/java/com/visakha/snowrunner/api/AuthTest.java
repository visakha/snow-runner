package com.visakha.snowrunner.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.visakha.snowrunner.execution.TestDoubles;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

/** Auth filter behavior: the placeholder header mechanism, both roles, liveness open. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDoubles.class)
class AuthTest extends ApiTestBase {

  @Test
  void unauthenticatedReturns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/runs"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
  }

  @Test
  void supportCannotTriggerReturns403() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/runs")
                .headers(supportHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"workflowId\":\"TRS_ONGOING\"}")
                .header("X-Idempotency-Key", "key-auth"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  @Test
  void supportCanReadReturns200() throws Exception {
    mockMvc.perform(get("/api/v1/runs").headers(supportHeaders())).andExpect(status().isOk());
  }

  @Test
  void livenessOpenWithoutAuth() throws Exception {
    mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
  }
}
