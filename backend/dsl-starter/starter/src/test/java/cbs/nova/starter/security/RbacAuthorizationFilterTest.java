package cbs.nova.starter.security;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.dsl.model.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Full (route, method) x role matrix for {@link RbacAuthorizationFilter}.
 *
 * <p>
 * Each cell verifies that:
 * <ul>
 * <li>A caller with the required (or higher) role is forwarded through the filter chain.</li>
 * <li>A caller with too-low a role gets a 403 response whose body is an {@link ErrorResponse}
 * envelope with {@code code = "FORBIDDEN"}.</li>
 * </ul>
 */
class RbacAuthorizationFilterTest {

  private ObjectMapper objectMapper;
  private RbacAuthorizationFilter filter;

  @BeforeEach
  void setUp() {
    objectMapper = JsonMapper.builder().build();
    filter = new RbacAuthorizationFilter(new RoleResolver(StarterConstants.DEFAULT_CLAIM_NAME),
            objectMapper);
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  // --- Accept-paths: ADMIN passes everything ----------------------------------

  @ParameterizedTest
  @CsvSource({
      // (method, path) — exercised under ADMIN
      "GET,    /api/dsl/objects/search",
      "POST,   /api/dsl/run/foo",
      "POST,   /api/dsl/preview/foo",
      "POST,   /api/dsl/explain/foo",
      "POST,   /api/executions/abc/cancel",
      "POST,   /api/dsl/reload",
      "POST,   /api/dsl/drafts/foo/save",
      "POST,   /api/dsl/drafts/foo/publish",
      "POST,   /api/dsl/drafts/foo/history/123/restore",
      "DELETE, /api/dsl/drafts/foo",
      "POST,   /api/dsl/files/foo.dsl",
      "POST,   /api/dsl/files/flush",
      "POST,   /api/dsl/files/by-name/foo",
      "POST,   /api/dsl/files/bulk",
      "POST,   /api/dsl/definitions/import",
      "POST,   /api/dsl/promote",
      "POST,   /api/dsl/schedules",
      "DELETE, /api/dsl/schedules/foo",
      "POST,   /api/dsl/auth/keys",
      "DELETE, /api/dsl/auth/keys/1",
      // T568 change-request approval gate
      "POST,   /api/dsl/drafts/foo/change-request",
      "POST,   /api/dsl/change-requests/1/approve",
      "POST,   /api/dsl/change-requests/1/reject"
  })
  void adminPassesEveryProtectedRoute(String method, String path) throws Exception {
    authenticateAs("admin", Role.ADMIN);

    MockHttpServletResponse response = invoke(method, path);

    assertThat(response.getStatus())
            .as("ADMIN must pass %s %s", method, path)
            .isEqualTo(200);
  }

  // --- Route role requirements ------------------------------------------------

  @ParameterizedTest
  @CsvSource({
      // RUNNER routes — RUNNER/ADMIN allowed, AUTHOR/OPERATOR allowed (higher), VIEWER denied
      "POST, /api/dsl/preview/foo,  RUNNER,   true",
      "POST, /api/dsl/run/foo,       RUNNER,   true",
      "POST, /api/dsl/explain/foo,   RUNNER,   true",
      "POST, /api/executions/abc/cancel, RUNNER, true",
      "POST, /api/dsl/run/foo,       VIEWER,   false",
      "POST, /api/executions/abc/cancel, VIEWER, false",
      // AUTHOR routes — RUNNER denied
      "POST, /api/dsl/reload,        AUTHOR,   true",
      "POST, /api/dsl/reload,        RUNNER,   false",
      "POST, /api/dsl/drafts/foo/save, AUTHOR, true",
      "POST, /api/dsl/drafts/foo/save, RUNNER, false",
      "DELETE, /api/dsl/drafts/foo,  AUTHOR,   true",
      "DELETE, /api/dsl/drafts/foo,  RUNNER,   false",
      "POST, /api/dsl/definitions/import, AUTHOR, true",
      "POST, /api/dsl/definitions/import, RUNNER, false",
      // OPERATOR promotion routes (T569) — AUTHOR denied
      "POST, /api/dsl/promote, OPERATOR, true",
      "POST, /api/dsl/promote, AUTHOR,   false",
      "POST, /api/dsl/promote, RUNNER,   false",
      // OPERATOR routes — AUTHOR denied
      "POST,   /api/dsl/schedules,    OPERATOR, true",
      "POST,   /api/dsl/schedules,    AUTHOR,   false",
      "DELETE, /api/dsl/schedules/foo, OPERATOR, true",
      "DELETE, /api/dsl/schedules/foo, AUTHOR,   false",
      // OPERATOR routes — admin API-key surface (T410)
      "POST,   /api/dsl/auth/keys,    OPERATOR, true",
      "POST,   /api/dsl/auth/keys,    AUTHOR,   false",
      "DELETE, /api/dsl/auth/keys/1,  OPERATOR, true",
      "DELETE, /api/dsl/auth/keys/1,  AUTHOR,   false",
      // T568 change-request approval gate — AUTHOR routes, RUNNER denied, OPERATOR passes
      "POST, /api/dsl/drafts/foo/change-request, AUTHOR,   true",
      "POST, /api/dsl/drafts/foo/change-request, RUNNER,   false",
      "GET,  /api/dsl/change-requests,           VIEWER,   true",
      "POST, /api/dsl/change-requests/1/approve, AUTHOR,   true",
      "POST, /api/dsl/change-requests/1/approve, OPERATOR, true",
      "POST, /api/dsl/change-requests/1/approve, RUNNER,   false",
      "POST, /api/dsl/change-requests/1/reject,  AUTHOR,   true",
      "POST, /api/dsl/change-requests/1/reject,  RUNNER,   false",
  })
  void routeRoleMatrixIsEnforced(String method, String path, String roleName,
          String shouldPass) throws Exception {
    Role callerRole = Role.valueOf(roleName);
    boolean expectPass = Boolean.parseBoolean(shouldPass);

    authenticateAs("caller", callerRole);
    MockHttpServletResponse response = invoke(method, path);

    if (expectPass) {
      assertThat(response.getStatus())
              .as("%s must pass %s %s", callerRole, method, path)
              .isEqualTo(200);
    } else {
      assertThat(response.getStatus())
              .as("%s must be denied %s %s", callerRole, method, path)
              .isEqualTo(403);
      ErrorResponse body = decode(response);
      assertThat(body.getCode()).isEqualTo("FORBIDDEN");
      assertThat(body.getMessage())
              .contains("required")
              .contains(callerRole.name());
    }
  }

  // --- GET always allowed for VIEWER ------------------------------------------

  @ParameterizedTest
  @CsvSource({
      "GET, /api/dsl/objects/search",
      "GET, /api/dsl/definitions",
      "GET, /api/dsl/drafts/foo",
      "GET, /api/dsl/drafts/foo/history",
      "GET, /api/dsl/diagnostics",
      "GET, /api/dsl/audit",
      "GET, /api/dsl/auth/keys",
      "GET, /api/executions",
      "GET, /api/executions/abc",
      "GET, /api/dsl/schedules",
      "GET, /api/dsl/files",
      "GET, /api/dsl/files/by-name/foo",
      "GET, /api/dsl/files/some/path.dsl",
      "GET, /api/webhooks/deliveries",
      "GET, /api/dsl/webhooks/deliveries",
      "GET, /api/dsl/change-requests"
  })
  void viewerPassesEveryGet(String method, String path) throws Exception {
    // No authentication at all → resolver returns VIEWER
    MockHttpServletResponse response = invoke(method, path);

    assertThat(response.getStatus())
            .as("GET must be allowed for VIEWER on %s", path)
            .isEqualTo(200);
  }

  // --- Anonymous mutating routes fail-closed ---------------------------------

  @Test
  void anonymousMutatingRunnerRouteIsDenied() throws Exception {
    MockHttpServletResponse response = invoke("POST", "/api/dsl/run/foo");

    assertThat(response.getStatus()).isEqualTo(403);
    ErrorResponse body = decode(response);
    assertThat(body.getCode()).isEqualTo("FORBIDDEN");
    assertThat(body.getMessage()).contains("RUNNER").contains("VIEWER");
  }

  @Test
  void anonymousMutatingAuthorRouteIsDenied() throws Exception {
    MockHttpServletResponse response = invoke("POST", "/api/dsl/reload");

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(decode(response).getMessage()).contains("AUTHOR");
  }

  @Test
  void anonymousMutatingOperatorRouteIsDenied() throws Exception {
    MockHttpServletResponse response = invoke("DELETE", "/api/dsl/schedules/foo");

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(decode(response).getMessage()).contains("OPERATOR");
  }

  // --- Unmatched mutating route defaults to OPERATOR (fail-closed) -----------

  @Test
  void unmatchedMutatingRouteDefaultsToOperatorStrictestSensible() throws Exception {
    authenticateAs("caller", Role.AUTHOR);
    MockHttpServletResponse response = invoke("POST", "/api/totally/new/mutating/route");

    assertThat(response.getStatus())
            .as("unmatched mutating route must default to OPERATOR (strict) and reject AUTHOR")
            .isEqualTo(403);
    assertThat(decode(response).getMessage()).contains("OPERATOR");
  }

  @Test
  void unmatchedMutatingRouteAcceptsOperator() throws Exception {
    authenticateAs("caller", Role.OPERATOR);
    MockHttpServletResponse response = invoke("POST", "/api/totally/new/mutating/route");

    assertThat(response.getStatus())
            .as("OPERATOR must satisfy the strict default")
            .isEqualTo(200);
  }

  @Test
  void unmatchedGetRouteDefaultsToViewer() throws Exception {
    MockHttpServletResponse response = invoke("GET", "/api/totally/new/read/route");

    assertThat(response.getStatus())
            .as("unmatched GET must default to VIEWER and let anonymous through")
            .isEqualTo(200);
  }

  // --- requiredRole unit (defensive coverage of the table) -------------------

  @Test
  void requiredRoleMatchesTable() {
    assertThat(filter.requiredRole(req("POST", "/api/dsl/preview/foo"))).isEqualTo(Role.RUNNER);
    assertThat(filter.requiredRole(req("POST", "/api/dsl/run/foo"))).isEqualTo(Role.RUNNER);
    assertThat(filter.requiredRole(req("POST", "/api/dsl/explain/foo"))).isEqualTo(Role.RUNNER);
    assertThat(filter.requiredRole(req("POST", "/api/executions/abc/cancel")))
            .isEqualTo(Role.RUNNER);
    assertThat(filter.requiredRole(req("POST", "/api/dsl/reload"))).isEqualTo(Role.AUTHOR);
    assertThat(filter.requiredRole(req("POST", "/api/dsl/drafts/foo/save")))
            .isEqualTo(Role.AUTHOR);
    assertThat(filter.requiredRole(req("DELETE", "/api/dsl/drafts/foo"))).isEqualTo(Role.AUTHOR);
    assertThat(filter.requiredRole(req("POST", "/api/dsl/files/some/file.dsl")))
            .isEqualTo(Role.AUTHOR);
    assertThat(filter.requiredRole(req("POST", "/api/dsl/files/flush"))).isEqualTo(Role.AUTHOR);
    assertThat(filter.requiredRole(req("POST", "/api/dsl/definitions/import")))
            .isEqualTo(Role.AUTHOR);
    assertThat(filter.requiredRole(req("POST", "/api/dsl/schedules"))).isEqualTo(Role.OPERATOR);
    assertThat(filter.requiredRole(req("DELETE", "/api/dsl/schedules/foo")))
            .isEqualTo(Role.OPERATOR);
    assertThat(filter.requiredRole(req("POST", "/api/dsl/auth/keys"))).isEqualTo(Role.OPERATOR);
    assertThat(filter.requiredRole(req("DELETE", "/api/dsl/auth/keys/1")))
            .isEqualTo(Role.OPERATOR);
    // T568 change-request approval gate
    assertThat(filter.requiredRole(req("POST", "/api/dsl/drafts/foo/change-request")))
            .isEqualTo(Role.AUTHOR);
    assertThat(filter.requiredRole(req("GET", "/api/dsl/change-requests")))
            .isEqualTo(Role.VIEWER);
    assertThat(filter.requiredRole(req("POST", "/api/dsl/change-requests/1/approve")))
            .isEqualTo(Role.AUTHOR);
    assertThat(filter.requiredRole(req("POST", "/api/dsl/change-requests/1/reject")))
            .isEqualTo(Role.AUTHOR);
    // reads
    assertThat(filter.requiredRole(req("GET", "/api/dsl/objects/search"))).isEqualTo(Role.VIEWER);
    assertThat(filter.requiredRole(req("GET", "/api/executions"))).isEqualTo(Role.VIEWER);
  }

  // --- helpers ----------------------------------------------------------------

  private MockHttpServletResponse invoke(String method, String path) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = (req, res) -> {
      HttpServletResponse http = (HttpServletResponse) res;
      http.setStatus(200);
    };
    filter.doFilter(request, response, chain);
    return response;
  }

  private static MockHttpServletRequest req(String method, String path) {
    return new MockHttpServletRequest(method, path);
  }

  private ErrorResponse decode(MockHttpServletResponse response) throws IOException {
    return objectMapper.readValue(response.getContentAsByteArray(), ErrorResponse.class);
  }

  private static void authenticateAs(String principal, Role role) {
    SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                    principal,
                    "n/a",
                    List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
  }

  @SuppressWarnings("unused")
  private static void headersReference() {
    HttpHeaders headers = new HttpHeaders();
    headers.toSingleValueMap();
  }
}
