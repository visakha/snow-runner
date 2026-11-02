package com.visakha.snowrunner.execution;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test doubles as primary beans, so the executor and preflight use fakes while the real caller and
 * check beans still exist in the context. Tests reset the fakes before each test; the context
 * itself is shared, the doubles' state is not.
 */
@TestConfiguration
public class TestDoubles {

  /** Provides the fake caller as the primary ProcedureCaller. */
  @Bean
  @Primary
  public FakeProcedureCaller fakeProcedureCaller() {
    return new FakeProcedureCaller();
  }

  /** Provides the fake check as the primary PreflightCheck. */
  @Bean
  @Primary
  public FakePreflightCheck fakePreflightCheck() {
    return new FakePreflightCheck();
  }
}
