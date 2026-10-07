package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.CbsDslRedactionProperties;
import cbs.nova.starter.security.JsonRedactor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Registers the {@link JsonRedactor} bean from {@code cbs.dsl.redaction.*} properties.
 *
 * <p>
 * The bean is unconditional so handlers can always inject it; when redaction is disabled the
 * returned redactor is a no-op.
 */
@Configuration
@EnableConfigurationProperties(CbsDslRedactionProperties.class)
public class RedactionConfiguration {

  @Bean
  @ConditionalOnMissingBean
  JsonRedactor jsonRedactor(ObjectMapper objectMapper, CbsDslRedactionProperties properties) {
    return new JsonRedactor(objectMapper, properties);
  }
}
