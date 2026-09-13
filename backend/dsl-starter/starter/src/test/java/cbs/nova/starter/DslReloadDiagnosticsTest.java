package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.utils.DslDefinitionLoader;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.utils.DefinitionLoader;
import cbs.nova.starter.exception.BuilderUnavailableException;
import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.DslReloadHandler;
import cbs.nova.starter.model.CompileModels.CompileRequest;
import cbs.nova.starter.model.CompileModels.CompileResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import tools.jackson.databind.ObjectMapper;

class DslReloadDiagnosticsTest {

  private DslReloadHandler resource;
  private final DslDefinitionLoader loader = new DefinitionLoader();
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
    resource = new DslReloadHandler(DslProperties.builder().build(), loader, null, null, null, null,
            null, null);
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  private void setSourceDir(String value) {
    resource = new DslReloadHandler(dslProperties(value), loader, null, null, null, null, null,
            null);
  }

  private static ServerRequest reloadRequest() {
    return ServerRequest.create(
            new MockHttpServletRequest("POST", "/api/dsl/reload"), List.of());
  }

  @Test
  void failedReloadSurfacesCompileDiagnostics() throws Exception {
    Path badDir = createTemporaryBrokenDslSourceDir();
    try {
      setSourceDir(badDir.toString());
      ServerResponse response = resource.reload(reloadRequest());
      assertThat(response.statusCode().value()).isEqualTo(500);

      var node = mapper.readTree(renderBody(response));
      assertThat(node.path("code").asString()).isEqualTo("RELOAD_FAILED");
      var diagnostics = node.path("diagnostics");
      assertThat(diagnostics.isArray()).isTrue();
      assertThat(diagnostics.size()).isPositive();
      assertThat(diagnostics.get(0).path("file").asString()).contains("Broken.java");
      assertThat(diagnostics.get(0).path("line").canConvertToLong()).isTrue();
      assertThat(diagnostics.get(0).path("message").asString()).isNotBlank();
      assertThat(diagnostics.get(0).path("severity").asString()).isEqualTo("error");
    } finally {
      deleteRecursively(badDir);
    }
  }

  @Test
  void failedReloadDiagnosticsIncludeJavacCode() throws Exception {
    Path badDir = createTemporaryBrokenDslSourceDir();
    try {
      setSourceDir(badDir.toString());
      ServerResponse response = resource.reload(reloadRequest());

      var node = mapper.readTree(renderBody(response));
      var first = node.path("diagnostics").get(0);
      assertThat(first.path("code").asText()).startsWith("compiler.");
    } finally {
      deleteRecursively(badDir);
    }
  }

  @Test
  void successfulReloadOmitsDiagnosticsField() throws Exception {
    Path sourceDir = createTemporaryDslSourceDir();
    try {
      setSourceDir(sourceDir.toString());
      ServerResponse response = resource.reload(reloadRequest());
      assertThat(response.statusCode().value()).isEqualTo(200);

      var node = mapper.readTree(renderBody(response));
      assertThat(node.path("diagnostics").isMissingNode()).isTrue();
    } finally {
      deleteRecursively(sourceDir);
    }
  }

  @Test
  void diagnosticsAreCappedAtTwenty() throws Exception {
    Path badDir = createTemporaryDslSourceDirWithManyErrors();
    try {
      setSourceDir(badDir.toString());
      ServerResponse response = resource.reload(reloadRequest());
      assertThat(response.statusCode().value()).isEqualTo(500);

      var node = mapper.readTree(renderBody(response));
      var diagnostics = node.path("diagnostics");
      assertThat(diagnostics.isArray()).isTrue();
      assertThat(diagnostics.size()).isEqualTo(20);
    } finally {
      deleteRecursively(badDir);
    }
  }

  private static String renderBody(ServerResponse response) throws Exception {
    var servletResponse = new MockHttpServletResponse();
    response.writeTo(
            new MockHttpServletRequest("POST", "/api/dsl/reload"),
            servletResponse,
            () -> List.of(new JacksonJsonHttpMessageConverter()));
    return servletResponse.getContentAsString();
  }

  @Test
  void reloadDelegatesCompilationToBuilderClient() throws Exception {
    Path sourceDir = createTemporaryPlainSourceDir();
    try {
      DslBuilderClient client = mock(DslBuilderClient.class);
      when(client.compile(any(CompileRequest.class)))
              .thenReturn(new CompileResult("s-1", true, List.of("Plain.class"),
                      List.of(), 7));
      when(client.downloadZip("s-1")).thenReturn(BuilderClientTestSupport.emptyZip());
      resource = new DslReloadHandler(dslProperties(sourceDir.toString()), loader, null, null,
              BuilderClientTestSupport.providerOf(client), null, null, null);

      ServerResponse response = resource.reload(reloadRequest());

      assertThat(response.statusCode().value()).isEqualTo(200);
      var node = mapper.readTree(renderBody(response));
      assertThat(node.path("load").path("total").asInt()).isEqualTo(0);
      verify(client).compile(argThatSourcesContain("Plain.java"));
    } finally {
      deleteRecursively(sourceDir);
    }
  }

  @Test
  void reloadSurfacesBuilderUnavailableAsCompilationFailure() throws Exception {
    Path sourceDir = createTemporaryPlainSourceDir();
    try {
      DslBuilderClient client = mock(DslBuilderClient.class);
      when(client.compile(any(CompileRequest.class)))
              .thenThrow(new BuilderUnavailableException("connection refused"));
      resource = new DslReloadHandler(dslProperties(sourceDir.toString()), loader, null, null,
              BuilderClientTestSupport.providerOf(client), null, null, null);

      ServerResponse response = resource.reload(reloadRequest());

      assertThat(response.statusCode().value()).isEqualTo(500);
      var node = mapper.readTree(renderBody(response));
      assertThat(node.path("code").asString()).isEqualTo("RELOAD_FAILED");
      assertThat(node.path("message").asString()).contains("DSL builder unavailable");
      assertThat(node.path("diagnostics").isArray()).isTrue();
    } finally {
      deleteRecursively(sourceDir);
    }
  }

  private Path createTemporaryPlainSourceDir() throws IOException {
    Path sourceDir = Files.createTempDirectory("reload-diagnostics-plain-");
    Files.writeString(sourceDir.resolve("Plain.java"), "class Plain {}\n");
    return sourceDir;
  }

  private static CompileRequest argThatSourcesContain(String fileName) {
    return ArgumentMatchers.argThat(
            request -> request != null && request.sources() != null
                    && request.sources().containsKey(fileName));
  }

  private Path createTemporaryDslSourceDir() throws IOException {
    Path sourceDir = Files.createTempDirectory("reload-diagnostics-source-");
    Path services = sourceDir.resolve("META-INF/services");
    Files.createDirectories(services);
    Files.writeString(services.resolve("cbs.nova.dsl.DslDefinitionProvider"),
            "ReloadDiagnosticsProvider\n");
    Files.writeString(sourceDir.resolve("ReloadDiagnosticsProvider.java"),
            """
                    import cbs.nova.dsl.Dsl;
                    import cbs.nova.dsl.DslDefinitionProvider;
                    import cbs.nova.dsl.DslObject;
                    import cbs.nova.dsl.Result;
                    import java.util.List;

                    public class ReloadDiagnosticsProvider implements DslDefinitionProvider {
                      @Override
                      public List<DslObject> definitions() {
                        return List.of(
                            Dsl.process("ReloadDiagnosticsProcess").execute(ctx -> Result.success("ok")).build());
                      }
                    }
                    """);
    return sourceDir;
  }

  private Path createTemporaryBrokenDslSourceDir() throws IOException {
    Path sourceDir = Files.createTempDirectory("reload-diagnostics-broken-");
    Files.writeString(sourceDir.resolve("Broken.java"),
            "this is not valid Java at all; { class Broken { ???");
    return sourceDir;
  }

  private Path createTemporaryDslSourceDirWithManyErrors() throws IOException {
    Path sourceDir = Files.createTempDirectory("reload-diagnostics-many-");
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

  private static DslProperties dslProperties(String sourceDir) {
    return DslProperties.builder().sourceDir(sourceDir).build();
  }

}
