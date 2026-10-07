package cbs.nova.starter.config.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the opt-in OIDC / JWT resource-server guard on the DSL REST API.
 *
 * <p>
 * When {@code cbs.security.oidc.enabled=false} (the default) no security filter chain is installed
 * and every DSL endpoint remains anonymous — matching the historical behaviour of the starter. When
 * enabled, requests to the protected paths (default {@code /api/**}, i.e. the entire REST surface
 * including {@code /api/v1/vhs/**} and {@code /api/webhooks/**}) must carry a Bearer JWT issued by
 * the configured Keycloak realm. Actuator health (default {@code /actuator/health/**}) and the
 * non-{@code /api} paths (root index, springdoc/Swagger) stay permitAll in both modes.
 *
 * <p>
 * T923 made the default secure-by-default: the previous default
 * {@code ["/api/dsl/**", "/api/executions/**"]} left registered routes such as
 * {@code GET /api/v1/vhs/tapes} and {@code GET /api/webhooks/deliveries} anonymous (reads fell back
 * to {@code Role.VIEWER}). Deployments that override {@code protected-paths} keep working exactly
 * as before; when the override leaves a registered {@code /api/**} route unprotected, the starter
 * logs a startup WARN (see {@code cbs.nova.starter.config.OidcProtectedPathAudit}).
 *
 * <p>
 * The actual JWT decoder is bootstrapped from
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} (or {@code jwk-set-uri} /
 * {@code public-key-location}); this class only carries the cbs-nova-specific toggle and the list
 * of protected path patterns.
 */
@ConfigurationProperties(prefix = "cbs.security.oidc")
public record CbsSecurityOidcProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("/api/**") List<String> protectedPaths,
        @DefaultValue("/actuator/health/**") List<String> permitAllPaths) {

  public CbsSecurityOidcProperties {
    if (protectedPaths == null || protectedPaths.isEmpty()) {
      protectedPaths = List.of("/api/**");
    }
    if (permitAllPaths == null) {
      permitAllPaths = List.of("/actuator/health/**");
    }
  }
}
