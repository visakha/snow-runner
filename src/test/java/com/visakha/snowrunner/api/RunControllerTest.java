package com.visakha.snowrunner.api;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.visakha.snowrunner.domain.Run;
import com.visakha.snowrunner.domain.RunId;
import com.visakha.snowrunner.domain.RunStatus;
import com.visakha.snowrunner.domain.RunStep;
import com.visakha.snowrunner.domain.RunTimestamp;
import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.domain.TargetDatabase;
import com.visakha.snowrunner.execution.TestDoubles;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** One test method per API call, per the coding guidelines. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDoubles.class)
class RunControllerTest extends ApiTestBase {

  private static final String TRIGGER_BODY = "{\"workflowId\":\"TRS_ONGOING\"}";

  @Test
  void triggerCreatesRun() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/runs")
                .headers(schedulerHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .content(TRIGGER_BODY)
                .header("X-Idempotency-Key", "key-1"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.workflowId").value("TRS_ONGOING"))
        .andExpect(jsonPath("$.status").value("SUCCEEDED"))
        .andExpect(jsonPath("$.stepsSucceeded").value(2))
        .andExpect(jsonPath("$.stepsSkipped").value(1))
        .andExpect(jsonPath("$.steps").doesNotExist());
  }

  @Test
  void triggerIdempotentRetryReturnsExistingRun() throws Exception {
    String first =
        mockMvc
            .perform(
                post("/api/v1/runs")
                    .headers(schedulerHeaders())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TRIGGER_BODY)
                    .header("X-Idempotency-Key", "key-dup"))
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String runId = first.split("\"runId\":\"")[1].split("\"")[0];
    mockMvc
        .perform(
            post("/api/v1/runs")
                .headers(schedulerHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .content(TRIGGER_BODY)
                .header("X-Idempotency-Key", "key-dup"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.runId").value(runId));
  }

  @Test
  void triggerMissingKeyReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/runs")
                .headers(schedulerHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .content(TRIGGER_BODY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("MISSING_IDEMPOTENCY_KEY"));
  }

  @Test
  void triggerUnknownWorkflowReturns404() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/runs")
                .headers(schedulerHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"workflowId\":\"NOPE\"}")
                .header("X-Idempotency-Key", "key-404"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("WORKFLOW_NOT_FOUND"));
  }

  @Test
  void triggerBusyReturns409() throws Exception {
    Run active =
        Run.create(
            RunId.generate(),
            "TRS_ONGOING",
            Source.TRS,
            new RunTimestamp(Instant.now()),
            TargetDatabase.SNOWFLAKE,
            "0.1-test",
            "0.1.0",
            "test-tree",
            "scheduler-test",
            "key-active",
            Instant.now());
    repository.insertRun(active);
    mockMvc
        .perform(
            post("/api/v1/runs")
                .headers(schedulerHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .content(TRIGGER_BODY)
                .header("X-Idempotency-Key", "key-busy"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("WORKFLOW_BUSY"))
        .andExpect(jsonPath("$.error.activeRunId").value(active.runId()));
  }

  @Test
  void resumeReturns202WithSteps() throws Exception {
    caller.failOn("TRS_ADDR_2", "boom");
    String failed =
        mockMvc
            .perform(
                post("/api/v1/runs")
                    .headers(schedulerHeaders())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TRIGGER_BODY)
                    .header("X-Idempotency-Key", "key-fail"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String runId = failed.split("\"runId\":\"")[1].split("\"")[0];
    caller.clearFailure();
    mockMvc
        .perform(post("/api/v1/runs/" + runId + "/resume").headers(schedulerHeaders()))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("SUCCEEDED"))
        .andExpect(jsonPath("$.attemptNo").value(2))
        .andExpect(jsonPath("$.steps").isArray());
    long ptyCalls =
        caller.calls().stream().filter(call -> call.stepId().equals("TRS_PTY_ONGOING")).count();
    assertEquals(1, ptyCalls, "a SUCCEEDED step is never re-executed by a resume");
  }

  @Test
  void resumeIllegalReturns409() throws Exception {
    String done =
        mockMvc
            .perform(
                post("/api/v1/runs")
                    .headers(schedulerHeaders())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TRIGGER_BODY)
                    .header("X-Idempotency-Key", "key-done"))
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String runId = done.split("\"runId\":\"")[1].split("\"")[0];
    mockMvc
        .perform(post("/api/v1/runs/" + runId + "/resume").headers(schedulerHeaders()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INVALID_TRANSITION"))
        .andExpect(jsonPath("$.error.currentStatus").value("SUCCEEDED"));
  }

  @Test
  void pauseReturns202AndStopsAfterCurrentStep() throws Exception {
    // Test-only threading: the trigger runs on a worker thread so this thread can
    // issue the pause while a step is in flight. The application itself stays
    // single-threaded; only the test harness uses a second thread.
    CountDownLatch latch = new CountDownLatch(1);
    caller.blockOn(latch);
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<MvcResult> future =
          pool.submit(
              () ->
                  mockMvc
                      .perform(
                          post("/api/v1/runs")
                              .headers(schedulerHeaders())
                              .contentType(MediaType.APPLICATION_JSON)
                              .content(TRIGGER_BODY)
                              .header("X-Idempotency-Key", "key-pause"))
                      .andReturn());
      String runId = null;
      long deadline = System.currentTimeMillis() + 10_000;
      while (runId == null && System.currentTimeMillis() < deadline) {
        if (!caller.calls().isEmpty()) {
          runId = caller.calls().get(0).runId();
        } else {
          Thread.sleep(50);
        }
      }
      assertNotNull(runId, "trigger should have started a step");
      mockMvc
          .perform(post("/api/v1/runs/" + runId + "/pause").headers(schedulerHeaders()))
          .andExpect(status().isAccepted());
      latch.countDown();
      future.get(30, TimeUnit.SECONDS);
      mockMvc
          .perform(get("/api/v1/runs/" + runId).headers(schedulerHeaders()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("PAUSED"))
          .andExpect(jsonPath("$.steps[2].stepId").value("TRS_ADDR_2"))
          .andExpect(jsonPath("$.steps[2].status").value("PENDING"));
    } finally {
      latch.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  void pauseNoopReturns200() throws Exception {
    Run paused = pausedFixture();
    mockMvc
        .perform(post("/api/v1/runs/" + paused.runId() + "/pause").headers(schedulerHeaders()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAUSED"));
  }

  @Test
  void cancelReturns202AndSkipsPending() throws Exception {
    Run paused = pausedFixture();
    mockMvc
        .perform(post("/api/v1/runs/" + paused.runId() + "/cancel").headers(schedulerHeaders()))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    mockMvc
        .perform(get("/api/v1/runs/" + paused.runId()).headers(schedulerHeaders()))
        .andExpect(jsonPath("$.steps[1].stepId").value("TRS_ADDR_2"))
        .andExpect(jsonPath("$.steps[1].status").value("SKIPPED"))
        .andExpect(jsonPath("$.steps[1].skippedReason").value("CANCELLED"));
  }

  @Test
  void listRunsReturnsPage() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/runs")
                .headers(schedulerHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .content(TRIGGER_BODY)
                .header("X-Idempotency-Key", "key-list"))
        .andExpect(status().isAccepted());
    mockMvc
        .perform(get("/api/v1/runs").headers(schedulerHeaders()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(50))
        .andExpect(jsonPath("$.totalPages").value(1));
    mockMvc
        .perform(get("/api/v1/runs?size=500").headers(schedulerHeaders()))
        .andExpect(jsonPath("$.size").value(200));
  }

  @Test
  void getRunReturnsDetail() throws Exception {
    String done =
        mockMvc
            .perform(
                post("/api/v1/runs")
                    .headers(schedulerHeaders())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TRIGGER_BODY)
                    .header("X-Idempotency-Key", "key-detail"))
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String runId = done.split("\"runId\":\"")[1].split("\"")[0];
    mockMvc
        .perform(get("/api/v1/runs/" + runId).headers(schedulerHeaders()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.steps", hasSize(3)));
  }

  @Test
  void getAttemptsReturnsHistory() throws Exception {
    caller.failOn("TRS_ADDR_2", "boom");
    String failed =
        mockMvc
            .perform(
                post("/api/v1/runs")
                    .headers(schedulerHeaders())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TRIGGER_BODY)
                    .header("X-Idempotency-Key", "key-att"))
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String runId = failed.split("\"runId\":\"")[1].split("\"")[0];
    caller.clearFailure();
    mockMvc
        .perform(post("/api/v1/runs/" + runId + "/resume").headers(schedulerHeaders()))
        .andExpect(status().isAccepted());
    mockMvc
        .perform(get("/api/v1/runs/" + runId + "/attempts").headers(schedulerHeaders()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attempts", hasSize(2)))
        .andExpect(jsonPath("$.attempts[0].attemptNo").value(1))
        .andExpect(jsonPath("$.attempts[1].attemptNo").value(2))
        .andExpect(jsonPath("$.attempts[1].steps", hasSize(1)));
  }

  @Test
  void getUnknownRunReturns404() throws Exception {
    mockMvc
        .perform(get("/api/v1/runs/nope").headers(schedulerHeaders()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RUN_NOT_FOUND"));
  }

  private Run pausedFixture() {
    Run run =
        Run.create(
            RunId.generate(),
            "TRS_ONGOING",
            Source.TRS,
            new RunTimestamp(Instant.now()),
            TargetDatabase.SNOWFLAKE,
            "0.1-test",
            "0.1.0",
            "test-tree",
            "scheduler-test",
            "key-paused",
            Instant.now());
    run.status(RunStatus.PAUSED);
    repository.insertRun(run);
    repository.insertStep(
        RunStep.pending(run.runId(), "TRS_PTY_ONGOING", 1, 1, "TRS_ETL_WRK.sp_test_pty"));
    repository.insertStep(
        RunStep.pending(run.runId(), "TRS_ADDR_2", 1, 3, "TRS_ETL_WRK.sp_test_addr"));
    return run;
  }
}
