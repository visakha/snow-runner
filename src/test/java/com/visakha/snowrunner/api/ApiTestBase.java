package com.visakha.snowrunner.api;

import com.visakha.snowrunner.execution.FakePreflightCheck;
import com.visakha.snowrunner.execution.FakeProcedureCaller;
import com.visakha.snowrunner.persistence.RunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared API-test setup: clean control tables and reset doubles before each test, so the shared
 * context never leaks state between tests.
 */
public abstract class ApiTestBase {

  @Autowired protected MockMvc mockMvc;

  @Autowired protected JdbcTemplate jdbc;

  @Autowired protected FakeProcedureCaller caller;

  @Autowired protected FakePreflightCheck preflight;

  @Autowired protected RunRepository repository;

  /** Clears runs, steps, and double state. */
  @BeforeEach
  void clean() {
    jdbc.execute("DELETE FROM RUN_STEP");
    jdbc.execute("DELETE FROM RUN");
    caller.reset();
    preflight.reset();
  }

  /** Headers for a scheduler caller. */
  protected HttpHeaders schedulerHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Caller-Id", "scheduler-test");
    headers.add("X-Caller-Role", "scheduler");
    return headers;
  }

  /** Headers for a support caller. */
  protected HttpHeaders supportHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Caller-Id", "support-test");
    headers.add("X-Caller-Role", "support");
    return headers;
  }
}
