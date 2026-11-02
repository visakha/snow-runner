package com.visakha.snowrunner.infra.config;

import com.visakha.snowrunner.catalog.StepCatalog;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The two connections, kept separate on purpose: the control store (RUN tables, Flyway, repository)
 * and the ETL target (CALLs, preflight). Exactly one ETL connection exists, chosen by the catalog's
 * targetDatabase flag at startup.
 */
@Configuration
public class DataSourceConfiguration {

  /** Builds the primary control-store connection. */
  @Bean
  @Primary
  public DataSource controlDataSource(ControlProperties properties) {
    return DataSourceBuilder.create()
        .type(HikariDataSource.class)
        .url(properties.url())
        .username(properties.username())
        .password(properties.password())
        .driverClassName(properties.driverClassName())
        .build();
  }

  /** Builds the ETL-target connection for the catalog's selected database. */
  @Bean("etlDataSource")
  public DataSource etlDataSource(
      StepCatalog catalog, SnowflakeProperties snowflake, OracleProperties oracle) {
    return switch (catalog.targetDatabase()) {
      case SNOWFLAKE ->
          DataSourceBuilder.create()
              .type(HikariDataSource.class)
              .url(snowflake.url())
              .username(snowflake.username())
              .password(snowflake.password())
              .driverClassName(snowflake.driverClassName())
              .build();
      case ORACLE ->
          DataSourceBuilder.create()
              .type(HikariDataSource.class)
              .url(oracle.url())
              .username(oracle.username())
              .password(oracle.password())
              .driverClassName(oracle.driverClassName())
              .build();
    };
  }

  /**
   * Builds the control-store template. It must exist and be primary: Boot's
   * NamedParameterJdbcTemplate auto-configuration picks the single-candidate JdbcTemplate, and
   * Spring Data builds the repository on it. Without this bean the only JdbcTemplate is the ETL
   * one, and the repository silently queries the ETL database instead of the control store.
   */
  @Bean("controlJdbcTemplate")
  @Primary
  public JdbcTemplate controlJdbcTemplate(@Qualifier("controlDataSource") DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }

  /** Builds the template the callers and preflight use. */
  @Bean("etlJdbcTemplate")
  public JdbcTemplate etlJdbcTemplate(@Qualifier("etlDataSource") DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }
}
