package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.starter.service.TemporalDslProcessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = IntegrationTestApplication.class, properties = {
    "csb.dsl.worker.enabled=false",
    "cbs.runs.max-input-bytes=100",
    "cbs.runs.max-output-bytes=50",
    "cbs.nova.process.async-db-save=false"
})
@Testcontainers
class DslRunPayloadSizeIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  @LocalServerPort
  private int port;

  private RestTemplate restTemplate;

  @Autowired
  private DataSource dataSource;

  @Autowired
  private DslRunRepository runRepository;

  @Autowired
  private TemporalDslProcessService processService;

  @Autowired
  private ObjectMapper objectMapper;

  @BeforeAll
  static void applyMigrations() throws SQLException {
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
            new ClassPathResource("db/migration/postgres/V1__init.sql"));
    populator.setContinueOnError(false);
    try (Connection connection = DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      populator.populate(connection);
    }
  }

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
    GlobalManager.globalManager().registerProcess(
            Dsl.process("Echo")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("a".repeat(200)))
                    .build());
    new JdbcTemplate(dataSource).execute("TRUNCATE TABLE dsl_runs");

    restTemplate = new RestTemplate();
    restTemplate.setErrorHandler(new NoOpErrorHandler());
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void oversizedRunRequestReturns413AndCreatesNoRow() throws Exception {
    String body = "{\"body\":\"" + "a".repeat(200) + "\"}";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> request = new HttpEntity<>(body, headers);

    ResponseEntity<String> response = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/dsl/run/Echo", request, String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());

    var error = objectMapper.readTree(response.getBody());
    assertThat(error.get("code").asText()).isEqualTo("PAYLOAD_TOO_LARGE");
    assertThat(error.get("entityName").asText()).isEqualTo("Echo");

    Long count = new JdbcTemplate(dataSource)
            .queryForObject("SELECT COUNT(*) FROM dsl_runs", Long.class);
    assertThat(count).isZero();
  }

  @Test
  void oversizedWorkerOutputIsTruncatedAndRunStaysCompleted() {
    TemporalDslProcessService.ProcessRun run = processService.startProcess("Echo", "trigger");
    Result<?> result = run.result().join();

    assertThat(result.isSuccess()).isTrue();

    DslRun persisted = runRepository.findByRunId(run.runId()).orElseThrow();
    assertThat(persisted.status()).isEqualTo(DslRunStatus.COMPLETED.name());
    assertThat(persisted.output()).isEqualTo("{\"truncated\":true,\"originalBytes\":202}");
    assertThat(persisted.error()).contains("truncated").contains("202 bytes");
  }

  static class NoOpErrorHandler implements ResponseErrorHandler {
    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
      return false;
    }

  }
}
