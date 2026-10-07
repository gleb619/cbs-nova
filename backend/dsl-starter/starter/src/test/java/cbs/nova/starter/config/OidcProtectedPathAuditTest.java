package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.properties.CbsSecurityOidcProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T923 — unit guard for the startup-WARN logic in {@link OidcProtectedPathAudit}.
 *
 * <p>
 * The security-critical cases: the secure-by-default set must cover every known {@code /api/**}
 * surface (VHS tapes, webhook deliveries, SSE streams); a pre-T923 narrow override must be flagged
 * as a gap; and explicit {@code permit-all-paths} entries are honoured as intentional public routes
 * (they belong in {@code OidcProtectedPathDriftTest}'s allowlist with a reason).
 */
class OidcProtectedPathAuditTest {

  private static final List<String> SAMPLE_ROUTES = List.of(
          "/",
          "/api/dsl/objects/search",
          "/api/executions",
          "/api/executions/{id}/events",
          "/api/dsl/dry-run/{traceId}/logs",
          "/api/v1/vhs/tapes",
          "/api/v1/vhs/tapes/{runId}",
          "/api/webhooks/deliveries");

  @Test
  @DisplayName("secure-by-default protected-paths cover every known /api route")
  void secureByDefaultCoversEverySampleApiRoute() {
    CbsSecurityOidcProperties defaults = new CbsSecurityOidcProperties(false, null, null);

    assertThat(OidcProtectedPathAudit.uncoveredApiRoutes(SAMPLE_ROUTES, defaults)).isEmpty();
  }

  @Test
  @DisplayName("pre-T923 narrow override flags VHS + webhook reads as anonymous gaps")
  void narrowOverrideFlagsVhsAndWebhookRoutes() {
    CbsSecurityOidcProperties narrow = new CbsSecurityOidcProperties(false,
            List.of("/api/dsl/**", "/api/executions/**"), List.of("/actuator/health/**"));

    assertThat(OidcProtectedPathAudit.uncoveredApiRoutes(SAMPLE_ROUTES, narrow))
            .containsExactly("/api/v1/vhs/tapes", "/api/v1/vhs/tapes/{runId}",
                    "/api/webhooks/deliveries");
  }

  @Test
  @DisplayName("explicit permit-all-paths entries are treated as intentional public routes")
  void permitAllPathsAreHonouredAsIntentional() {
    CbsSecurityOidcProperties withAllowance = new CbsSecurityOidcProperties(false,
            List.of("/api/dsl/**"), List.of("/actuator/health/**", "/api/webhooks/**"));

    assertThat(OidcProtectedPathAudit.uncoveredApiRoutes(SAMPLE_ROUTES, withAllowance))
            .doesNotContain("/api/webhooks/deliveries")
            .contains("/api/v1/vhs/tapes");
  }
}
