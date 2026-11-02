package com.visakha.snowrunner.infra.config;

import com.visakha.snowrunner.catalog.StepCatalog;
import com.visakha.snowrunner.catalog.YamlStepCatalog;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * Loads the catalog once at startup. A catalog problem fails startup: no partial catalog ever
 * executes, and no run can start against an unvalidated file.
 */
@Configuration
public class CatalogConfiguration {

  /** Loads the catalog bean from the configured location. */
  @Bean
  public StepCatalog stepCatalog(CatalogProperties properties) throws IOException {
    var resource = new DefaultResourceLoader().getResource(properties.location());
    try (InputStream input = resource.getInputStream()) {
      return YamlStepCatalog.load(input, properties.location());
    }
  }
}
