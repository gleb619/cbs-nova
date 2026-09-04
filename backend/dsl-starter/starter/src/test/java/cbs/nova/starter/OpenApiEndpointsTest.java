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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "csb.dsl.worker.enabled=false")
class OpenApiEndpointsTest {

  private static final HttpClient CLIENT = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .build();

  @LocalServerPort
  private int port;

  @Test
  void apiDocsReturnsJsonWithDslPaths() throws Exception {
    HttpResponse<String> response = get("/v3/api-docs");
    assertThat(response.statusCode()).isEqualTo(200);
    String body = response.body();
    assertThat(body)
            .as("OpenAPI JSON body should declare the DSL paths")
            .contains("/api/dsl/preview/{name}")
            .contains("/api/dsl/run/{name}")
            .contains("/api/dsl/explain/{name}")
            .contains("/api/dsl/reload")
            .contains("/api/dsl/processes")
            .contains("/api/dsl/transactions")
            .contains("/api/dsl/helpers")
            .contains("/api/dsl/definitions");
  }

  @Test
  void apiDocsExposesTopLevelInfo() throws Exception {
    HttpResponse<String> response = get("/v3/api-docs");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
            .contains("\"title\":\"cbs-nova DSL API\"");
  }

  @Test
  void swaggerUiHtmlResponds() throws Exception {
    HttpResponse<String> response = get("/swagger-ui.html");
    int status = response.statusCode();
    assertThat(status)
            .as("swagger-ui.html should respond 200 or 302 redirect")
            .isIn(200, 302);
    if (status == 302) {
      String location = response.headers().firstValue("Location").orElse("");
      assertThat(location).contains("/swagger-ui/");
    }
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
