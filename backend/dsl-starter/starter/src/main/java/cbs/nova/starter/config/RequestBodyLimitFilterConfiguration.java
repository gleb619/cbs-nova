package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.CbsNovaWebProperties;
import cbs.nova.starter.web.RequestBodyLimitFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the global request-body cap filter ({@link RequestBodyLimitFilter}) as a Spring bean so
 * it can be wired onto every functional router via {@code RouterFunctions.Builder#filter}.
 *
 * <p>
 * The router configurations under {@code config/router/} depend on this bean. Routers apply it via
 * {@code .filter(requestBodyLimitFilter)} so that {@code POST} / {@code PUT} / {@code PATCH}
 * requests with bodies larger than {@code cbs.nova.web.max-request-bytes} are rejected with HTTP
 * 413 {@code PAYLOAD_TOO_LARGE} before any controller runs.
 */
@Configuration
@EnableConfigurationProperties(CbsNovaWebProperties.class)
public class RequestBodyLimitFilterConfiguration {

  @Bean
  public RequestBodyLimitFilter requestBodyLimitFilter(CbsNovaWebProperties properties) {
    return new RequestBodyLimitFilter(properties);
  }
}
