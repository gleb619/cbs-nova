package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies the actuator health endpoint hardening (T752):
 * <ul>
 * <li>Anonymous {@code GET /actuator/health} returns {@code {"status": "UP"}} only — no
 * {@code components} object — when {@code show-details: when-authorized} and
 * {@code show-components: when-authorized} are in effect (the launcher default).</li>
 * <li>Liveness/readiness probes stay anonymous and answer with plain {@code UP/DOWN} regardless of
 * authentication state.</li>
 * <li>Authenticated callers ({@code .with(jwt())}) see the full {@code components} block —
 * anonymous detail-leak IS a security regression and must fail this test.</li>
 * </ul>
 * Each slice boots with {@code cbs.security.oidc.enabled=true} so the JWT resource-server chain is
 * registered; an anonymous request to the permit-all {@code /actuator/health/**} path still reaches
 * the handler, but Spring Security's {@code SecurityContext} has no {@code Authentication}, which
 * is exactly the condition Spring Boot's {@code Show.WHEN_AUTHORIZED} resolves to "never show
 * details".
 */
class HealthDetailsAuthorizationTest {

  private static final HttpClient CLIENT = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .build();

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Nested
  @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = HealthDetailsAuthorizationTest.TestApplication.class, properties = {
      "cbs.security.oidc.enabled=true",
      "cbs.dsl.worker.enabled=false",
      "management.endpoint.health.show-details=when-authorized",
      "management.endpoint.health.show-components=when-authorized",
      // Force Temporal readiness down so the indicator exposes its detail keys (dsl,
      // dslReadiness, db) — anonymous callers must NOT see them.
      "cbs.health.temporal.fail-status=DOWN",
      "temporal.connection-target=127.0.0.1:1",
      "cbs.health.temporal.timeout=PT0.1S"
  })
  class WhenAuthorized {

    @LocalServerPort
    private int port;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Test
    void anonymousHealthReturnsStatusOnlyAndNoComponents() throws Exception {
      JsonNode body = httpGetJson("/actuator/health");

      assertThat(body.path("status").asString())
              .as("anonymous /actuator/health must still report the aggregate status")
              .isEqualTo("DOWN");
      assertThat(body.has("components"))
              .as("anonymous /actuator/health must not expose the components block (T752)")
              .isFalse();
      assertThat(body.has("details"))
              .as("anonymous /actuator/health must not expose the details block (T752)")
              .isFalse();
      // The anonymous response shape is exactly {status, groups?} — never includes db, dsl,
      // dslReadiness or any other component key that would leak indicator state.
      assertThat(body.path("db").isMissingNode())
              .as("anonymous /actuator/health must not include the db component (T752)")
              .isTrue();
      assertThat(body.path("dsl").isMissingNode())
              .as("anonymous /actuator/health must not include the dsl component (T752)")
              .isTrue();
      assertThat(body.path("dslReadiness").isMissingNode())
              .as("anonymous /actuator/health must not include the dslReadiness component (T752)")
              .isTrue();
    }

    @Test
    void anonymousLivenessProbeReturnsStatusOnly() throws Exception {
      JsonNode body = httpGetJson("/actuator/health/liveness");

      assertThat(body.path("status").asString()).isEqualTo("UP");
      assertThat(body.has("components"))
              .as("anonymous /actuator/health/liveness must not expose components")
              .isFalse();
    }

    @Test
    void anonymousReadinessProbeReturnsStatusOnly() throws Exception {
      JsonNode body = httpGetJson("/actuator/health/readiness");

      assertThat(body.path("status").asString())
              .as("readiness should report DOWN when Temporal probe is forced to fail")
              .isEqualTo("DOWN");
      assertThat(body.has("components"))
              .as("anonymous /actuator/health/readiness must not expose components (T752)")
              .isFalse();
    }

    @Test
    void authenticatedHealthExposesComponentsAndDetails() throws Exception {
      // MockMvc + springSecurity() injects the Authentication into the SecurityContext for this
      // request, which is the signal Spring Boot's Show.WHEN_AUTHORIZED reads.
      MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
              .apply(springSecurity())
              .build();

      MvcResult result = mockMvc.perform(get("/actuator/health").with(jwt()))
              .andExpect(status().is(503))
              .andReturn();

      JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
      assertThat(body.path("status").asString()).isEqualTo("DOWN");
      assertThat(body.path("components").has("dslReadiness"))
              .as("authenticated /actuator/health must surface the dslReadiness component")
              .isTrue();
      assertThat(body.path("components").path("dslReadiness").path("status").asString())
              .isEqualTo("DOWN");
    }

    @Test
    void authenticatedLivenessProbeExposesComponents() throws Exception {
      MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
              .apply(springSecurity())
              .build();

      MvcResult result = mockMvc.perform(get("/actuator/health/liveness").with(jwt()))
              .andExpect(status().isOk())
              .andReturn();

      JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
      // Group probes inherit the parent's show-details setting but only show configured
      // indicators — liveness must remain UP and surface the livenessState component.
      assertThat(body.path("status").asString()).isEqualTo("UP");
      assertThat(body.path("components").has("livenessState")).isTrue();
    }

    @Test
    void anonymousHealthStillReturnsHttp503WhenStatusIsDown() throws Exception {
      // Spring Boot maps DOWN -> 503 by default. The security change must not change that
      // mapping: anonymous callers still see the DOWN signal via the HTTP status, they just
      // no longer see which component is failing.
      HttpResponse<String> response = httpGet("/actuator/health");
      assertThat(response.statusCode())
              .as("anonymous probe must surface DOWN via HTTP status (T752 hardening)")
              .isEqualTo(503);
    }

    private HttpResponse<String> httpGet(String path) throws Exception {
      HttpRequest request = HttpRequest.newBuilder()
              .uri(URI.create("http://localhost:" + port + path))
              .timeout(Duration.ofSeconds(10))
              .GET()
              .build();
      return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode httpGetJson(String path) throws Exception {
      HttpResponse<String> response = httpGet(path);
      return MAPPER.readTree(response.body());
    }
  }

  @SpringBootApplication(scanBasePackages = "cbs.nova.starter")
  @Import(HealthDetailsAuthorizationTest.MockJwtDecoderConfig.class)
  static class TestApplication {
    public static void main(String[] args) {
      SpringApplication.run(TestApplication.class, args);
    }
  }

  /**
   * Mock JWT decoder so the resource-server chain has something to wire without the auto-config
   * trying to reach a (non-existent) Keycloak at startup. Spring Security's
   * BearerTokenAuthenticationFilter rejects unauthenticated requests to {@code /api/**} before the
   * decoder is called; for the JWT-injected MockMvc requests the decoder is bypassed by the
   * {@code SecurityMockMvcRequestPostProcessors.jwt()} post-processor.
   */
  @TestConfiguration
  static class MockJwtDecoderConfig {

    @Bean
    @Primary
    JwtDecoder jwtDecoder() {
      return mock(JwtDecoder.class);
    }
  }
}
