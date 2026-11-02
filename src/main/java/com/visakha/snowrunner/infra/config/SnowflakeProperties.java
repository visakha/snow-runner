package com.visakha.snowrunner.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Snowflake ETL-target connection, from snowrunner.snowflake.*. */
@ConfigurationProperties(prefix = "snowrunner.snowflake")
public record SnowflakeProperties(
    String url, String username, String password, String driverClassName) {}
