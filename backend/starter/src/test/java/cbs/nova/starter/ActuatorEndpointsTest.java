package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "dsl.worker.enabled=false")
class ActuatorEndpointsTest {

  private static final HttpClient CLIENT = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .build();

  @LocalServerPort
  private int port;

  @Test
  void healthEndpointIsUpAndExposesDslComponent() throws Exception {
    HttpResponse<String> response = get("/actuator/health");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("dsl");
  }

  @Test
  void infoEndpointReturns200() throws Exception {
    HttpResponse<String> response = get("/actuator/info");
    assertThat(response.statusCode()).isEqualTo(200);
  }

  @Test
  void metricsEndpointListsAvailableMetrics() throws Exception {
    HttpResponse<String> response = get("/actuator/metrics");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("jvm.memory.used");
  }

  private HttpResponse<String> get(String path) throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
    return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
  }

  @SpringBootApplication
  static class TestApplication {
    public static void main(String[] args) {
      SpringApplication.run(TestApplication.class, args);
    }
  }
}
