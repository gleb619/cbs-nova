package cbs.nova.starter.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for the JWT helper ({@code cbs.dsl.helper.jwt}).
 */
@ConfigurationProperties(prefix = "cbs.dsl.helper.jwt")
public record JwtProperties(
        @DefaultValue("HS256") String defaultAlgorithm,
        @DefaultValue("3600") Long defaultTtlSeconds) {

  public JwtProperties {
    defaultAlgorithm = (defaultAlgorithm == null || defaultAlgorithm.isBlank())
            ? "HS256"
            : defaultAlgorithm;
    defaultTtlSeconds = defaultTtlSeconds == null ? 3600L : defaultTtlSeconds;
  }
}
