package com.visakha.snowrunner.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Control-store connection, from snowrunner.control.*. */
@ConfigurationProperties(prefix = "snowrunner.control")
public record ControlProperties(
    String url, String username, String password, String driverClassName) {}
