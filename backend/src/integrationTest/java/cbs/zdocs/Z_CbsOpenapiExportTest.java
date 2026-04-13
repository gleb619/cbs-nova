package cbs.zdocs;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.app.config.OpenApiConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Exports the OpenAPI specification to the build directory.
 *
 * <p>Run via {@code ./gradlew :backend:exportOpenApi}.
 */
// CHECKSTYLE:OFF
@SuppressWarnings("checkstyle:TypeName")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = Z_CbsOpenapiExportTest.OpenApiExportTestApplication.class,
    properties = {"spring.jpa.hibernate.ddl-auto=none", """
        spring.autoconfigure.exclude=\
        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,\
        org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,\
        org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,\
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,\
        org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration"""})
class Z_CbsOpenapiExportTest {
  // CHECKSTYLE:ON

  @LocalServerPort
  private int port;

  @Test
  void exportOpenApiSpecToBuildDirectory() throws IOException, InterruptedException {
    HttpClient client = HttpClient.newHttpClient();
    String openApiUrl = String.format("http://localhost:%d/v3/api-docs", port);

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(openApiUrl))
        .header("Accept", "application/json")
        .GET()
        .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isNotBlank();

    Path outputFile = Path.of("build", "openapi.json");
    Files.createDirectories(outputFile.getParent());
    Files.writeString(
        outputFile,
        response.body(),
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE);

    assertThat(Files.exists(outputFile)).isTrue();
    assertThat(Files.size(outputFile)).isGreaterThan(0);

    ObjectMapper objectMapper = new ObjectMapper();
    Object openApiSpec = objectMapper.readValue(response.body(), Object.class);
    assertThat(openApiSpec).isNotNull();

    assertThat(response.body()).contains("\"openapi\"");
    assertThat(response.body()).contains("\"info\"");
    assertThat(response.body()).contains("\"paths\"");
    assertThat(response.body()).contains("\"components\"");
    assertThat(response.body()).contains("\"/api/settings\"");
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({OpenApiConfig.class, OpenApiExportTestSecurity.class})
  static class OpenApiExportTestApplication {}

  @TestConfiguration
  static class OpenApiExportTestSecurity {

    @Bean
    SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
      http.csrf(csrf -> csrf.disable())
          .authorizeHttpRequests(auth ->
              auth.requestMatchers("/v3/api-docs").permitAll().anyRequest().authenticated());
      return http.build();
    }
  }
}
