package cbs.nova.starter.security;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.model.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parity spec for {@link RbacAuthorizationFilter}: pins every hardcoded route rule as behaviour
 * before the manifest migration, then re-runs unchanged after migration to prove zero diff.
 *
 * <p>
 * The filter bean is exercised through a minimal Spring context so the same test compiles and
 * passes whether the filter uses the legacy hardcoded rule list or the manifest-driven lookup.
 */
@SpringBootTest(classes = RbacAuthorizationFilterParityTest.TestApp.class, webEnvironment = WebEnvironment.MOCK, properties = {
    "cbs.dsl.auth.rbac.enabled=true", "cbs.dsl.worker.enabled=false"})

class RbacAuthorizationFilterParityTest {

  @SpringBootApplication(scanBasePackages = "cbs.nova.starter")
  static class TestApp {

    public static void main(String[] args) {
      SpringApplication.run(TestApp.class, args);
    }
  }

  @Autowired
  private RbacAuthorizationFilter filter;

  private final ObjectMapper objectMapper = JsonMapper.builder().build();

  @DynamicPropertySource
  static void manifestPath(DynamicPropertyRegistry registry) {
    Path repo = Path.of("").toAbsolutePath().normalize().getParent().getParent().getParent();
    Path manifest = repo.resolve("app/dsl/src/main/resources/piece-manifest.yaml");
    registry.add("cbs.dsl.manifest.path", () -> manifest.toUri().toString());
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  static Stream<Arguments> routeRules() {
    return Stream.of(
            // RUNNER routes
            Arguments.of("POST", "/api/dsl/signals/foo", Role.RUNNER),
            Arguments.of("POST", "/api/dsl/preview/foo", Role.RUNNER),
            Arguments.of("POST", "/api/dsl/run/foo", Role.RUNNER),
            Arguments.of("POST", "/api/dsl/explain/foo", Role.RUNNER),
            Arguments.of("POST", "/api/executions/abc/cancel", Role.RUNNER),
            // AUTHOR routes
            Arguments.of("POST", "/api/dsl/reload", Role.AUTHOR),
            Arguments.of("POST", "/api/dsl/manifest/reload", Role.AUTHOR),
            Arguments.of("POST", "/api/dsl/drafts/foo/save", Role.AUTHOR),
            Arguments.of("POST", "/api/dsl/drafts/foo/publish", Role.AUTHOR),
            Arguments.of("POST", "/api/dsl/drafts/foo/history/123/restore", Role.AUTHOR),
            Arguments.of("DELETE", "/api/dsl/drafts/foo", Role.AUTHOR),
            Arguments.of("POST", "/api/dsl/drafts/foo/change-request", Role.AUTHOR),
            Arguments.of("GET", "/api/dsl/change-requests", Role.VIEWER),
            Arguments.of("POST", "/api/dsl/change-requests/1/approve", Role.AUTHOR),
            Arguments.of("POST", "/api/dsl/change-requests/1/reject", Role.AUTHOR),
            Arguments.of("POST", "/api/dsl/files/foo.dsl", Role.AUTHOR),
            Arguments.of("POST", "/api/dsl/files/flush", Role.AUTHOR),
            Arguments.of("POST", "/api/dsl/definitions/import", Role.AUTHOR),
            // OPERATOR routes
            Arguments.of("POST", "/api/dsl/promote", Role.OPERATOR),
            Arguments.of("POST", "/api/dsl/promote/foo", Role.OPERATOR),
            Arguments.of("POST", "/api/dsl/schedules", Role.OPERATOR),
            Arguments.of("DELETE", "/api/dsl/schedules/foo", Role.OPERATOR),
            Arguments.of("POST", "/api/dsl/schedules/foo/pause", Role.OPERATOR),
            Arguments.of("POST", "/api/dsl/schedules/foo/resume", Role.OPERATOR),
            Arguments.of("POST", "/api/dsl/auth/keys", Role.OPERATOR),
            Arguments.of("DELETE", "/api/dsl/auth/keys/1", Role.OPERATOR));
  }

  @ParameterizedTest
  @MethodSource("routeRules")
  void requiredRoleMatchesCurrentRules(String method, String path, Role expected) {
    assertThat(filter.requiredRole(req(method, path)))
            .as("required role for %s %s", method, path)
            .isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("routeRules")
  void roleDecisionMatrixMatchesCurrentRules(String method, String path, Role required)
          throws Exception {
    // Every role at or above the required role passes; lower roles are denied.
    for (Role caller : Role.values()) {
      if (caller == Role.ADMIN) {
        continue; // tested separately
      }
      authenticateAs("caller", caller);
      MockHttpServletResponse response = invoke(method, path);
      if (caller.satisfies(required)) {
        assertThat(response.getStatus())
                .as("%s must pass %s %s (required=%s)", caller, method, path, required)
                .isEqualTo(200);
      } else {
        assertThat(response.getStatus())
                .as("%s must be denied %s %s (required=%s)", caller, method, path, required)
                .isEqualTo(403);
        ErrorResponse body = decode(response);
        assertThat(body.getCode()).isEqualTo("FORBIDDEN");
        assertThat(body.getMessage()).contains(required.name());
      }
    }
  }

  @ParameterizedTest
  @MethodSource("routeRules")
  void adminPassesEveryProtectedRoute(String method, String path, Role ignored) throws Exception {
    authenticateAs("admin", Role.ADMIN);

    MockHttpServletResponse response = invoke(method, path);

    assertThat(response.getStatus())
            .as("ADMIN must pass %s %s", method, path)
            .isEqualTo(200);
  }

  @Test
  void anonymousMutatingRunnerRouteIsDenied() throws Exception {
    MockHttpServletResponse response = invoke("POST", "/api/dsl/run/foo");

    assertThat(response.getStatus()).isEqualTo(403);
    ErrorResponse body = decode(response);
    assertThat(body.getCode()).isEqualTo("FORBIDDEN");
    assertThat(body.getMessage()).contains("RUNNER").contains("VIEWER");
  }

  @Test
  void unmatchedGetRouteDefaultsToViewer() throws Exception {
    MockHttpServletResponse response = invoke("GET", "/api/totally/new/read/route");

    assertThat(response.getStatus())
            .as("unmatched GET must default to VIEWER")
            .isEqualTo(200);
  }

  @Test
  void unmatchedMutatingRouteDefaultsToOperatorStrictFailClosed() throws Exception {
    authenticateAs("caller", Role.AUTHOR);
    MockHttpServletResponse response = invoke("POST", "/api/totally/new/mutating/route");

    assertThat(response.getStatus())
            .as("unmatched mutating route must default to OPERATOR and reject AUTHOR")
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
}
