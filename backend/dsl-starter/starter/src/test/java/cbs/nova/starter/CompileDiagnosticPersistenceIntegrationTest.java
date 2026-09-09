package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.DefinitionLoader;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.DslDiagnosticsHandler;
import cbs.nova.starter.controller.DslReloadHandler;
import cbs.nova.starter.model.CompileDiagnosticSource;
import cbs.nova.starter.persistence.CompileDiagnosticRecordRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = WebhookTestApplication.class)
@Sql(scripts = {"classpath:db/migration/h2/V1__init.sql",
    "classpath:db/migration/h2/V4__dsl_compile_diagnostics.sql",
    "classpath:sql/truncate-dsl-compile-diagnostics.sql"})
@TestPropertySource(properties = {
    "csb.dsl.worker.enabled=false"
})
class CompileDiagnosticPersistenceIntegrationTest {

  @Autowired
  private CompileDiagnosticRecordRepository repository;

  private final DefinitionLoader loader = new DefinitionLoader();
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void failedReloadPersistsAllDiagnosticsAndResponseIsCappedAtTwenty() throws Exception {
    Path badDir = createTemporaryDslSourceDirWithManyErrors();
    try {
      DslProperties props = DslProperties.builder().sourceDir(badDir.toString()).build();
      DslReloadHandler handler = new DslReloadHandler(props, loader, null, null, null,
              providerOf(repository));

      ServerResponse response = handler.reload(reloadRequest());

      assertThat(response.statusCode().value()).isEqualTo(500);
      var node = mapper.readTree(renderBody(response));
      var diagnostics = node.path("diagnostics");
      assertThat(diagnostics.isArray()).isTrue();
      assertThat(diagnostics.size()).isEqualTo(20);

      var persisted = repository.search(badDir.toString(), 0, 100);
      assertThat(persisted.total()).isGreaterThan(20);
      assertThat(persisted.items()).allSatisfy(
              r -> assertThat(r.source()).isEqualTo(CompileDiagnosticSource.RELOAD.name()));
    } finally {
      deleteRecursively(badDir);
    }
  }

  @Test
  void diagnosticPersistenceSwallowsRepositoryFailure() throws Exception {
    CompileDiagnosticRecordRepository throwingRepository = mock(
            CompileDiagnosticRecordRepository.class);
    doThrow(new RuntimeException("database down")).when(throwingRepository)
            .insertAll(any(CompileDiagnosticSource.class), any(), any());

    Path badDir = createTemporaryBrokenDslSourceDir();
    try {
      DslProperties props = DslProperties.builder().sourceDir(badDir.toString()).build();
      DslReloadHandler handler = new DslReloadHandler(props, loader, null, null, null,
              providerOf(throwingRepository));

      ServerResponse response = handler.reload(reloadRequest());

      assertThat(response.statusCode().value()).isEqualTo(500);
      var node = mapper.readTree(renderBody(response));
      assertThat(node.path("code").asText()).isEqualTo("RELOAD_FAILED");
      assertThat(node.path("diagnostics").isArray()).isTrue();
    } finally {
      deleteRecursively(badDir);
    }
  }

  @Test
  void diagnosticsHandlerPaginationAndDefinitionFilter() throws Exception {
    repository.insertAll(CompileDiagnosticSource.RELOAD, "filter-a",
            List.of(new cbs.nova.starter.model.CompileDiagnostic("a.java", 1L, 1L, "msg", "error",
                    null)));
    repository.insertAll(CompileDiagnosticSource.PUBLISH, "filter-b",
            List.of(new cbs.nova.starter.model.CompileDiagnostic("b.java", 1L, 1L, "msg", "error",
                    null)));

    DslDiagnosticsHandler handler = new DslDiagnosticsHandler(repository);
    var request = diagnosticsRequest("filter-a");

    ServerResponse response = handler.list(request);

    assertThat(response.statusCode().value()).isEqualTo(200);
    var node = mapper.readTree(renderBody(response));
    assertThat(node.path("total").asInt()).isEqualTo(1);
    assertThat(node.path("items")).hasSize(1);
    assertThat(node.path("items").get(0).path("definition").asText()).isEqualTo("filter-a");
    assertThat(node.path("offset").asInt()).isEqualTo(0);
    assertThat(node.path("limit").asInt()).isEqualTo(50);
  }

  private static ServerRequest reloadRequest() {
    return ServerRequest.create(
            new MockHttpServletRequest("POST", "/api/dsl/reload"), List.of());
  }

  private static ServerRequest diagnosticsRequest(String definition) {
    var req = new MockHttpServletRequest("GET", "/api/dsl/diagnostics");
    if (definition != null) {
      req.addParameter("definition", definition);
    }
    return ServerRequest.create(req, List.of());
  }

  private String renderBody(ServerResponse response) throws Exception {
    var servletResponse = new MockHttpServletResponse();
    response.writeTo(
            new MockHttpServletRequest("GET", "/api/dsl/diagnostics"),
            servletResponse,
            () -> List.of(new JacksonJsonHttpMessageConverter()));
    return servletResponse.getContentAsString();
  }

  private static Path createTemporaryBrokenDslSourceDir() throws IOException {
    Path sourceDir = Files.createTempDirectory("diagnostics-broken-");
    Files.writeString(sourceDir.resolve("Broken.java"),
            "this is not valid Java at all; { class Broken { ???");
    return sourceDir;
  }

  private static Path createTemporaryDslSourceDirWithManyErrors() throws IOException {
    Path sourceDir = Files.createTempDirectory("diagnostics-many-");
    StringBuilder body = new StringBuilder("public class ManyErrors {\n");
    for (int i = 1; i <= 30; i++) {
      body.append("  int a").append(i).append(" = unknown").append(i).append(";\n");
    }
    body.append("}\n");
    Files.writeString(sourceDir.resolve("ManyErrors.java"), body.toString());
    return sourceDir;
  }

  private void deleteRecursively(Path path) throws IOException {
    try (Stream<Path> stream = Files.walk(path)) {
      stream.sorted((a, b) -> -a.compareTo(b)).forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (IOException e) {
          // ignore
        }
      });
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> providerOf(T bean) {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(bean);
    return provider;
  }
}
