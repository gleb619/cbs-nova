package cbs.nova.starter.security;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.web.ApiKeyAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Unit tests for {@link RoleResolver}. Drives the resolver through both the API-key branch (header
 * inspection) and the JWT branch (SecurityContext inspection), verifying claim precedence,
 * case-insensitivity and missing-claim fallback to {@link Role#VIEWER}.
 */
class RoleResolverTest {

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  // --- API-key branch ----------------------------------------------------------

  @Test
  void apiKeyHeaderResolvesToAdminRegardlessOfSecurityContext() {
    RoleResolver resolver = new RoleResolver("roles");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "any-non-blank-value");

    Role resolved = resolver.resolve(request);

    assertThat(resolved).isEqualTo(Role.ADMIN);
  }

  @Test
  void blankApiKeyHeaderDoesNotTriggerApiKeyBranch() {
    SecurityContextHolder.getContext().setAuthentication(authenticated("alice", "VIEWER"));
    RoleResolver resolver = new RoleResolver("roles");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "   ");

    assertThat(resolver.resolve(request)).isEqualTo(Role.VIEWER);
  }

  // --- JWT branch --------------------------------------------------------------

  @Test
  void jwtWithRolesClaimArrayIsMappedCaseInsensitively() {
    setJwt(jwt(Map.of("roles", List.of("viewer", "Runner"))));
    RoleResolver resolver = new RoleResolver("roles");

    assertThat(resolver.resolve(new MockHttpServletRequest()))
            .isEqualTo(Role.RUNNER);
  }

  @Test
  void jwtWithHighestRoleWins() {
    setJwt(jwt(Map.of("roles", List.of("viewer", "operator"))));
    RoleResolver resolver = new RoleResolver("roles");

    assertThat(resolver.resolve(new MockHttpServletRequest()))
            .isEqualTo(Role.OPERATOR);
  }

  @Test
  void jwtMissingRolesClaimFallsBackToViewer() {
    setJwt(jwt(Map.of("some-other-claim", "value")));
    RoleResolver resolver = new RoleResolver("roles");

    assertThat(resolver.resolve(new MockHttpServletRequest()))
            .isEqualTo(Role.VIEWER);
  }

  @Test
  void jwtWithUnknownRoleValueStillFallsBackToViewer() {
    setJwt(jwt(Map.of("roles", List.of("nope", "still-nope"))));
    RoleResolver resolver = new RoleResolver("roles");

    assertThat(resolver.resolve(new MockHttpServletRequest()))
            .isEqualTo(Role.VIEWER);
  }

  @Test
  void defaultClaimAlsoReadsScopeSpaceDelimitedPerRfc6749() {
    setJwt(jwt(Map.of("scope", "viewer runner author")));
    RoleResolver resolver = new RoleResolver("roles");

    assertThat(resolver.resolve(new MockHttpServletRequest()))
            .isEqualTo(Role.AUTHOR);
  }

  @Test
  void defaultClaimAlsoReadsScpClaim() {
    setJwt(jwt(Map.of("scp", "admin")));
    RoleResolver resolver = new RoleResolver("roles");

    assertThat(resolver.resolve(new MockHttpServletRequest()))
            .isEqualTo(Role.ADMIN);
  }

  @Test
  void configurableClaimNameIsHonoured() {
    setJwt(jwt(Map.of("cbs_roles", List.of("operator"))));
    RoleResolver resolver = new RoleResolver("cbs_roles");

    assertThat(resolver.resolve(new MockHttpServletRequest()))
            .isEqualTo(Role.OPERATOR);
  }

  @Test
  void customClaimDoesNotPickUpScopeClaim() {
    // A custom claim name like "cbs_roles" should be exclusive — we must NOT also read "scope",
    // otherwise mis-configured IdPs would silently widen the role set.
    setJwt(jwt(Map.of("cbs_roles", List.of("viewer"), "scope", "dsl.admin")));
    RoleResolver resolver = new RoleResolver("cbs_roles");

    assertThat(resolver.resolve(new MockHttpServletRequest()))
            .isEqualTo(Role.VIEWER);
  }

  @Test
  void grantedAuthoritiesAreAlsoConsulted() {
    // Phase-2 mappers (e.g. JwtGrantedAuthoritiesConverter) emit SCOPE_xxx authorities. Make
    // sure the resolver picks them up so future role-format migrations don't break enforcement.
    setAuth(new UsernamePasswordAuthenticationToken(
            "svc",
            "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_AUTHOR"))));
    RoleResolver resolver = new RoleResolver("roles");

    assertThat(resolver.resolve(new MockHttpServletRequest()))
            .isEqualTo(Role.AUTHOR);
  }

  // --- No-auth branch ----------------------------------------------------------

  @Test
  void noAuthenticationResolvesToViewer() {
    RoleResolver resolver = new RoleResolver("roles");

    assertThat(resolver.resolve(new MockHttpServletRequest()))
            .isEqualTo(Role.VIEWER);
  }

  @Test
  void anonymousAuthenticationIsTreatedAsViewer() {
    setAuth(new AnonymousAuthenticationToken(
            "key", "anon",
            List.of(new SimpleGrantedAuthority("ROLE_AUTHOR"))));
    RoleResolver resolver = new RoleResolver("roles");

    assertThat(resolver.resolve(new MockHttpServletRequest()))
            .isEqualTo(Role.VIEWER);
  }

  // --- Helpers -----------------------------------------------------------------

  private static void setJwt(Jwt jwt) {
    JwtAuthenticationToken token = new JwtAuthenticationToken(jwt);
    // JwtAuthenticationToken(Jwt) defaults to authenticated=false (the production code path is
    // JwtAuthenticationProvider, which calls setAuthenticated(true)). Mirror that here so the
    // resolver sees an authenticated principal.
    token.setAuthenticated(true);
    setAuth(token);
  }

  private static void setAuth(org.springframework.security.core.Authentication auth) {
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  private static Jwt jwt(Map<String, Object> claims) {
    return new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "none"),
            claims);
  }

  private static org.springframework.security.core.Authentication authenticated(
          String name, String role) {
    return new UsernamePasswordAuthenticationToken(
            name, "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
  }

  /** Sanity helper so the {@link HttpServletRequest} import isn't trimmed by an optimiser. */
  @SuppressWarnings("unused")
  private static HttpServletRequest any() {
    return new MockHttpServletRequest();
  }
}
