package com.visakha.snowrunner.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Catalog location and tree version, from snowrunner.catalog.*. The tree version records which ETL
 * tree the catalog targets (v3 in the bundled catalog); it records the choice, it does not make it.
 */
@ConfigurationProperties(prefix = "snowrunner.catalog")
public record CatalogProperties(String location, String treeVersion) {}
