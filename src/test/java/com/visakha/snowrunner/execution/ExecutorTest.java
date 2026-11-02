package com.visakha.snowrunner.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.visakha.snowrunner.domain.Run;
import com.visakha.snowrunner.domain.RunStatus;
import com.visakha.snowrunner.persistence.RunRepository;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Executor behavior that HTTP tests cannot prove precisely: resume reuses the original timestamp
 * and never re-executes a SUCCEEDED step, and preflight fails closed before any CALL.
 */
@SpringBootTest
@Import(TestDoubles.class)
class ExecutorTest {

  @Autowired private RunExecutor executor;

  @Autowired private RunRepository repository;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private FakeProcedureCaller caller;

  @Autowired private FakePreflightCheck preflight;

  /** Clears runs, steps, and double state. */
  @BeforeEach
  void clean() {
    jdbc.execute("DELETE FROM RUN_STEP");
    jdbc.execute("DELETE FROM RUN");
    caller.reset();
    preflight.reset();
  }

  @Test
  void resumeReusesTimestampAndSkipsSucceeded() {
    caller.failOn("TRS_ADDR_2", "boom");
    Run failed = executor.trigger("TRS_ONGOING", "key-exec", "scheduler-test").run();
    assertEquals(RunStatus.FAILED, failed.status());
    caller.clearFailure();
    Run resumed = executor.resume(failed.runId(), "scheduler-test");
    assertEquals(RunStatus.SUCCEEDED, resumed.status());
    assertEquals(2, resumed.currentAttemptNo());
    // Compared within one microsecond: H2 TIMESTAMP stores microseconds, so a
    // nanosecond trigger timestamp rounds on the round-trip through the database
    // (truncating both sides still misses by that rounding). Snowflake
    // TIMESTAMP_NTZ keeps nanoseconds, so production round-trips exactly. A
    // regenerated timestamp would differ by milliseconds, not one microsecond.
    assertTrue(
        Duration.between(failed.runTs(), resumed.runTs()).abs().toNanos() <= 1000,
        "a resume reuses the original P_RUN_TS");
    long ptyCalls =
        caller.calls().stream().filter(call -> call.stepId().equals("TRS_PTY_ONGOING")).count();
    long addrCalls =
        caller.calls().stream().filter(call -> call.stepId().equals("TRS_ADDR_2")).count();
    assertEquals(1, ptyCalls, "a SUCCEEDED step is never re-executed by a resume");
    assertEquals(2, addrCalls, "a FAILED step is re-executed by a resume");
  }

  @Test
  void triggerWithMissingProcedureFailsPreflight() {
    preflight.markMissing("TRS_PTY_ONGOING");
    Run run = executor.trigger("TRS_ONGOING", "key-preflight", "scheduler-test").run();
    assertEquals(RunStatus.FAILED, run.status());
    assertTrue(run.errorMessage().contains("Preflight failed"));
    assertTrue(run.errorMessage().contains("TRS_ETL_WRK.sp_test_pty"));
    assertEquals(0, caller.calls().size(), "preflight failure issues no CALL");
  }
}
