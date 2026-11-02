package com.visakha.snowrunner.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Oracle ETL-target connection, from snowrunner.oracle.*. */
@ConfigurationProperties(prefix = "snowrunner.oracle")
public record OracleProperties(
    String url, String username, String password, String driverClassName) {}
