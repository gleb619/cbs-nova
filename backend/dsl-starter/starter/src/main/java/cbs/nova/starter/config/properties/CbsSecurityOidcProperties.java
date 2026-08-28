package cbs.nova.starter.config.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the opt-in OIDC / JWT resource-server guard on the DSL REST API.
 *
 * <p>When {@code cbs.security.oidc.enabled=false} (the default) no security filter chain
 * is installed and every DSL endpoint remains anonymous — matching the historical
 * behaviour of the starter. When enabled, requests to the protected paths
 * ({@code /api/dsl/**} and {@code /api/executions/**}) must carry a Bearer JWT
 * issued by the configured Keycloak realm. Actuator health and springdoc/Swagger
 * paths stay permitAll in both modes.
 *
 * <p>The actual JWT decoder is bootstrapped by Spring Boot from
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} (or
 * {@code jwk-set-uri} / {@code public-key-location}); this class only carries the
 * cbs-nova-specific toggle and the list of protected path patterns.
 */
@ConfigurationProperties(prefix = "cbs.security.oidc")
public record CbsSecurityOidcProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue({"/api/dsl/**", "/api/executions/**"}) List<String> protectedPaths,
        @DefaultValue("/actuator/health/**") List<String> permitAllPaths) {

  public CbsSecurityOidcProperties {
    if (protectedPaths == null || protectedPaths.isEmpty()) {
      protectedPaths = List.of("/api/dsl/**", "/api/executions/**");
    }
    if (permitAllPaths == null) {
      permitAllPaths = List.of("/actuator/health/**");
    }
  }
}
