package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.DslProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loud operator-facing WARN for the silent-auth-disablement footgun: an operator configures a
 * non-blank {@code cbs.dsl.auth.api-key} expecting every {@code /api/*} request to require the
 * {@code X-Api-Key} header, but forgets to also set {@code cbs.dsl.auth.enabled=true}. Because
 * {@link ApiKeyAuthFilterConfiguration} is gated by that property, no filter beans are ever created
 * and every request flows through unauthenticated.
 *
 * <p>
 * This configuration is intentionally NOT gated by {@code cbs.dsl.auth.enabled} — it must always
 * load so the check actually fires for the very deployments that misconfigured the property.
 * Implemented as a {@link SmartInitializingSingleton} bean so the warning runs once at startup,
 * after the application context is fully wired and property bindings are resolved, with no
 * dependence on the (possibly absent) auth filter beans. Using
 * {@link SmartInitializingSingleton#afterSingletonsInstantiated()} instead of an
 * {@code ApplicationRunner} keeps the check reachable from lightweight
 * {@code ApplicationContextRunner} tests, which refresh the context but do not invoke runners.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(DslProperties.class)
public class ApiKeyAuthMisconfigurationWarning {

  @Bean
  public SmartInitializingSingleton apiKeyAuthMisconfigurationCheck(DslProperties dslProperties) {
    return () -> {
      var auth = dslProperties.getAuth();
      if (auth.getApiKey() != null && !auth.getApiKey().isBlank() && !auth.isEnabled()) {
        log.warn("cbs.dsl.auth.api-key is configured but cbs.dsl.auth.enabled=false — "
                + "ApiKeyAuthFilter beans are NOT registered and every /api/* request flows through "
                + "unauthenticated. Set cbs.dsl.auth.enabled=true to activate the API-key guard.");
      }
    };
  }
}
