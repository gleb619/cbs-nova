package cbs.nova.server.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * Registers starter components from {@code cbs.nova.starter} without re-importing the
 * auto-configuration classes. The starter's own configuration classes are loaded by Spring Boot
 * auto-configuration; this scan only picks up {@code @Component} helpers, services, and mappers.
 */
@Configuration
@ComponentScan(
    basePackages = "cbs.nova.starter",
    excludeFilters = {
      @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = Configuration.class),
      @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = AutoConfiguration.class)
    })
public class StarterComponentScanConfiguration {
}
