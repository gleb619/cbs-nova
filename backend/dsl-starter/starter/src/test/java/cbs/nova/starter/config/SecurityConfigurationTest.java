package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Verifies the OIDC resource-server guard behaviour and, critically, the
 * default-off guarantee: with {@code cbs.security.oidc.enabled} unset (or false)
 * the {@link SecurityConfiguration#oidcSecurityFilterChain} bean is absent from
 * the context and every endpoint remains anonymous.
 *
 * <p>The default-off guarantee is the single most important constraint of this
 * task: an existing test (e.g. {@code JdbcDslRunRepositoryTest},
 * {@code OpenApiEndpointsTest}, {@code ActuatorEndpointsTest}) must run
 * unmodified, and that test must observe no security-filter behaviour.
 */
class SecurityConfigurationTest {

  private static final HttpClient CLIENT = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .build();

  @Nested
  @SpringBootTest(
          webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
          classes = SecurityConfigurationTest.TestApplication.class,
          properties = {
              "dsl.worker.enabled=false"
          })
  class DefaultOffGuarantee {

    @Autowired
    private ApplicationContext context;

    @LocalServerPort
    private int port;

    @Test
    void contextExposesExactlyOnePermissiveSecurityFilterChain() {
      Map<String, SecurityFilterChain> chains = context.getBeansOfType(SecurityFilterChain.class);
      assertThat(chains)
              .as("default mode must register exactly one SecurityFilterChain (the permitAll one)")
              .hasSize(1);
      // The OIDC chain bean is the one we explicitly do NOT want here.
      assertThat(chains.keySet())
              .noneMatch(name -> name.toLowerCase().contains("oidc"));
    }

    @Test
    void anonymousRequestToDslApiIsPermitted() throws Exception {
      // /api/dsl/processes is read-only and unauthenticated by default.
      HttpResponse<String> response = httpGet("/api/dsl/processes");
      assertThat(response.statusCode())
              .as("default mode must serve /api/dsl/processes anonymously (must not be 401)")
              .isNotEqualTo(401);
    }

    @Test
    void anonymousRequestToExecutionsIsPermitted() throws Exception {
      HttpResponse<String> response = httpGet("/api/executions");
      assertThat(response.statusCode())
              .as("default mode must serve /api/executions anonymously")
              .isNotEqualTo(401);
    }

    @Test
    void anonymousRequestToActuatorHealthIsPermitted() throws Exception {
      HttpResponse<String> response = httpGet("/actuator/health");
      assertThat(response.statusCode())
              .as("actuator/health stays permitAll in both modes")
              .isEqualTo(200);
    }

    private HttpResponse<String> httpGet(String path) throws Exception {
      HttpRequest request = HttpRequest.newBuilder()
              .uri(URI.create("http://localhost:" + port + path))
              .timeout(Duration.ofSeconds(10))
              .GET()
              .build();
      return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }
  }

  @Nested
  @SpringBootTest(
          webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
          classes = SecurityConfigurationTest.GuardEnabledTestApp.class,
          properties = {
              "cbs.security.oidc.enabled=true",
              "dsl.worker.enabled=false"
          })
  class GuardEnabled {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @LocalServerPort
    private int port;

    @Test
    void oidcFilterChainIsRegistered() {
      Map<String, SecurityFilterChain> chains = context.getBeansOfType(SecurityFilterChain.class);
      assertThat(chains)
              .as("enabled mode must register the oidcSecurityFilterChain bean")
              .containsKey("oidcSecurityFilterChain");
    }

    @Test
    void anonymousRequestToProtectedPathIsChallengedWith401() throws Exception {
      HttpResponse<String> response = httpGet("/api/dsl/processes");
      assertThat(response.statusCode())
              .as("missing/invalid token on protected path must return 401, not 500")
              .isEqualTo(401);
      assertThat(response.headers().firstValue(HttpHeaders.WWW_AUTHENTICATE))
              .as("401 must include a WWW-Authenticate header")
              .isPresent()
              .get()
              .asString()
              .startsWith("Bearer");
    }

    @Test
    void anonymousRequestToExecutionsIsChallengedWith401() throws Exception {
      HttpResponse<String> response = httpGet("/api/executions");
      assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void anonymousRequestToActuatorHealthIsStillPermitted() throws Exception {
      HttpResponse<String> response = httpGet("/actuator/health");
      assertThat(response.statusCode())
              .as("actuator/health must stay permitAll even when OIDC is enabled")
              .isEqualTo(200);
    }

    @Test
    void mockJwtIsAcceptedByProtectedEndpoint() throws Exception {
      // Build a MockMvc that applies Spring Security's filter chain on top of the
      // WebApplicationContext, then verify that a mocked valid JWT lets the request
      // through to the handler.
      MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
              .apply(springSecurity())
              .build();
      mockMvc.perform(get("/api/dsl/processes").with(jwt()))
              .andExpect(status().isOk());
    }

    @Test
    void missingJwtIsRejectedByProtectedEndpoint() throws Exception {
      MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
              .apply(springSecurity())
              .build();
      mockMvc.perform(get("/api/dsl/processes"))
              .andExpect(status().isUnauthorized())
              .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE));
    }

    private HttpResponse<String> httpGet(String path) throws Exception {
      HttpRequest request = HttpRequest.newBuilder()
              .uri(URI.create("http://localhost:" + port + path))
              .timeout(Duration.ofSeconds(10))
              .GET()
              .build();
      return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }
  }

  /**
   * Application config for the default-off (OIDC disabled) test slice. Identical
   * pattern to the other integration tests in this module: a bare
   * {@code @SpringBootApplication} that lets the starter's auto-configurations
   * wire everything.
   */
  @SpringBootApplication(scanBasePackages = "cbs.nova.starter")
  static class TestApplication {
    public static void main(String[] args) {
      SpringApplication.run(TestApplication.class, args);
    }
  }

  /**
   * Application config for the OIDC-enabled test slice. We provide a mock
   * {@link JwtDecoder} bean so the OIDC chain has something to wire without
   * the resource-server auto-config trying to reach a (non-existent) Keycloak
   * at startup. Anonymous requests are still rejected with 401 by Spring
   * Security's BearerTokenAuthenticationFilter before the decoder is ever
   * called, which is exactly the behaviour we want to assert.
   */
  @SpringBootApplication(scanBasePackages = "cbs.nova.starter")
  @Import(SecurityConfigurationTest.MockJwtDecoderConfig.class)
  static class GuardEnabledTestApp {
    public static void main(String[] args) {
      SpringApplication.run(GuardEnabledTestApp.class, args);
    }
  }

  @TestConfiguration
  static class MockJwtDecoderConfig {

    @Bean
    @Primary
    JwtDecoder jwtDecoder() {
      return mock(JwtDecoder.class);
    }
  }
}
