package com.visakha.snowrunner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point. Configuration properties live under snowrunner.*.
 *
 * <p>This codebase implements the clean-ETL constitution
 * (docs/constitution/clean-etl-constitution-guide.md): stateless runs driven by a YAML catalog
 * contract, boundaries the code never reaches across (Java never reads C_S_* or V_* tables and
 * never evaluates a spine predicate), and names that say source, dimension, and phase. The
 * constitution, not this class, is where those rules are argued.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SnowRunnerApplication {

  /** Boots the application. */
  public static void main(String[] args) {
    SpringApplication.run(SnowRunnerApplication.class, args);
  }
}
