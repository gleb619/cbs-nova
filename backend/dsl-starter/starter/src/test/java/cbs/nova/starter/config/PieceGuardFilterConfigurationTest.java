package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import cbs.nova.starter.security.PieceGuardFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

/**
 * Pin the guard's servlet ordering: it must run after the +1 (API key) and +2 (RBAC + global rate
 * limit) bands so the principal and role are resolved before pre-checks evaluate, and before the
 * route handler.
 */
class PieceGuardFilterConfigurationTest {

  @Test
  void registrationRunsAfterPrincipalResolvingFiltersAndBeforeRouteHandler() {
    PieceGuardFilterConfiguration configuration = new PieceGuardFilterConfiguration();
    FilterRegistrationBean<PieceGuardFilter> registration = configuration
            .pieceGuardFilterRegistration(mock(PieceGuardFilter.class));

    assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 3);
    assertThat(registration.getUrlPatterns()).containsExactly("/api/*");
  }
}
