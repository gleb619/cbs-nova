package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.service.TemporalDslProcessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "csb.dsl.worker.enabled=false")
class TemporalDslProcessMetricsActuatorTest {

  private static final HttpClient CLIENT = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .build();

  @LocalServerPort
  private int port;

  @Autowired
  private TemporalDslProcessService processService;

  @Test
  void actuatorMetricsExposesDslRunMetersAfterRun() throws Exception {
    processService.startProcess("not-a-defined-process", Map.of()).result().join();

    HttpResponse<String> response = get("/actuator/metrics/dsl.run.count");

    assertThat(response.statusCode()).isEqualTo(200);
    String body = response.body();
    assertThat(body).contains("\"name\":\"dsl.run.count\"");
    assertThat(body).contains("FAILED");
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
