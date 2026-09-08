package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end regression tests for the liveness/readiness split.
 *
 * <p>
 * The {@code dslReadiness} indicator is wired into the readiness group; the liveness group only
 * includes Spring Boot's built-in {@code livenessState}. Therefore a Temporal outage must drop
 * readiness but must never make liveness go DOWN.
 *
 * <p>
 * The probe target is pointed at a closed port so the Temporal reachability check returns
 * {@code unreachable} without needing a real Temporal server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = HealthProbeEndpointsTest.TestApplication.class, properties = "csb.dsl.worker.enabled=false")
@TestPropertySource(properties = {
    "cbs.health.temporal.fail-status=DOWN",
    "temporal.connection-target=127.0.0.1:1",
    "cbs.health.temporal.timeout=PT0.1S"
})
class HealthProbeEndpointsTest {

  private static final HttpClient CLIENT = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .build();

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @LocalServerPort
  private int port;

  @Test
  void livenessIsUpEvenWhenTemporalIsUnreachable() throws Exception {
    JsonNode body = getJson("/actuator/health/liveness");

    assertThat(body.path("status").asString()).isEqualTo("UP");
    assertThat(body.path("components").path("livenessState").path("status").asString())
            .isEqualTo("UP");
    assertThat(body.path("components").has("dslReadiness")).isFalse();
    assertThat(body.path("components").has("db")).isFalse();
  }

  @Test
  void readinessIsDownWhenTemporalIsUnreachableAndFailStatusIsDown() throws Exception {
    JsonNode body = getJson("/actuator/health/readiness");

    assertThat(body.path("status").asString()).isEqualTo("DOWN");
    assertThat(body.path("components").path("dslReadiness").path("status").asString())
            .isEqualTo("DOWN");
    JsonNode temporal = body.path("components").path("dslReadiness").path("details")
            .path("temporal");
    assertThat(temporal.path("reachable").asBoolean()).isFalse();
    assertThat(temporal.path("target").asString()).isEqualTo("127.0.0.1:1");
    assertThat(temporal.has("error")).isTrue();
  }

  @Test
  void readinessGroupIncludesDslReadinessAndLivenessGroupIncludesLivenessStateOnly()
          throws Exception {
    JsonNode readiness = getJson("/actuator/health/readiness");
    assertThat(readiness.path("components").has("dslReadiness")).isTrue();
    assertThat(readiness.path("components").path("readinessState").path("status").asString())
            .isEqualTo("UP");

    JsonNode liveness = getJson("/actuator/health/liveness");
    assertThat(liveness.path("components").has("livenessState")).isTrue();
    assertThat(liveness.path("components").has("dslReadiness")).isFalse();
  }

  @Test
  void plainHealthPreservesDslComponentDetailKeys() throws Exception {
    JsonNode body = getJson("/actuator/health");

    assertThat(body.path("status").asString()).isEqualTo("DOWN");
    JsonNode dsl = body.path("components").path("dsl");
    assertThat(dsl.path("status").asString()).isEqualTo("DOWN");
    JsonNode details = dsl.path("details");
    assertThat(details.has("processes")).isTrue();
    assertThat(details.has("transactions")).isTrue();
    assertThat(details.has("helpers")).isTrue();
    JsonNode temporal = details.path("temporal");
    assertThat(temporal.path("reachable").asBoolean()).isFalse();
    assertThat(temporal.path("target").asString()).isEqualTo("127.0.0.1:1");
    assertThat(temporal.has("configuredTaskQueues")).isTrue();
  }

  private JsonNode getJson(String path) throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
    HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    return MAPPER.readTree(response.body());
  }

  @SpringBootApplication
  static class TestApplication {
    public static void main(String[] args) {
      SpringApplication.run(TestApplication.class, args);
    }
  }
}
