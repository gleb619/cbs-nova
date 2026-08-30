package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.DslDraftRouterConfiguration;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.DslDraftHandler;
import cbs.nova.starter.controller.DslReloadHandler;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import cbs.nova.starter.model.VcsModels.DraftResponse;
import cbs.nova.starter.model.VcsModels.DraftSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import tools.jackson.databind.ObjectMapper;

class DslDraftResourceTest {

  private DslDraftHandler handler;
  private Path sourceDir;
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() throws IOException {
    sourceDir = Files.createTempDirectory("dsl-draft-test-");
    DslProperties props = new DslProperties(sourceDir.toString(), null, null, null, null);
    handler = new DslDraftHandler(props, new DslReloadHandler(props, null), mapper);
  }

  @AfterEach
  void tearDown() throws IOException {
    if (sourceDir != null && Files.exists(sourceDir)) {
      deleteRecursively(sourceDir);
    }
  }

  private static final List<HttpMessageConverter<?>> CONVERTERS = List
          .of(new InputStreamHttpMessageConverter());

  private static ServerRequest postRequest(String path) {
    return postRequest(path, "foo");
  }

  private static ServerRequest postRequest(String path, String name) {
    var req = new MockHttpServletRequest("POST", path);
    req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("name", name));
    req.setContentType("application/json");
    req.setContent(
            ("{\"name\":\"" + name
                    + "\",\"type\":\"process\",\"status\":\"Draft\",\"version\":\"1\"}")
                    .getBytes());
    return ServerRequest.create(req, CONVERTERS);
  }

  private static ServerRequest deleteRequest(String name, String path) {
    var req = new MockHttpServletRequest("DELETE", path);
    req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("name", name));
    return ServerRequest.create(req, CONVERTERS);
  }

  private static ServerRequest getRequest(String path, Map<String, String> pathVariables) {
    var req = new MockHttpServletRequest("GET", path);
    if (pathVariables != null && !pathVariables.isEmpty()) {
      req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, pathVariables);
    }
    return ServerRequest.create(req, CONVERTERS);
  }

  @Test
  void savePersistsDraftJson() throws Exception {
    ServerResponse response = handler.save(postRequest("/api/dsl/drafts/foo/save"));
    assertThat(response.statusCode().value()).isEqualTo(200);
    Path draft = sourceDir.resolve(".workbench/drafts/foo.json");
    assertThat(draft).exists();
    String body = Files.readString(draft);
    assertThat(body).contains("\"name\" : \"foo\"");
    assertThat(body).contains("\"status\" : \"Draft\"");
  }

  @Test
  void publishPersistsPublishedJson() throws Exception {
    ServerResponse response = handler.publish(postRequest("/api/dsl/drafts/foo/publish"));
    assertThat(response.statusCode().value()).isEqualTo(200);
    Path published = sourceDir.resolve(".workbench/published/foo.json");
    assertThat(published).exists();
    String body = Files.readString(published);
    assertThat(body).contains("\"status\" : \"Published\"");
  }

  @Test
  void deleteRemovesDraftJson() throws Exception {
    handler.save(postRequest("/api/dsl/drafts/foo/save"));
    Path draft = sourceDir.resolve(".workbench/drafts/foo.json");
    assertThat(draft).exists();

    ServerResponse response = handler.delete(deleteRequest("foo", "/api/dsl/drafts/foo"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    assertThat(draft).doesNotExist();
  }

  @Test
  void deleteReturns404WhenDraftUnknown() throws Exception {
    ServerResponse response = handler.delete(deleteRequest("foo", "/api/dsl/drafts/foo"));

    assertThat(response.statusCode().value()).isEqualTo(404);
  }

  @Test
  void deleteReturns409WhenSourceDirBlank() throws Exception {
    handler = new DslDraftHandler(
            new DslProperties("", null, null, null, null),
            new DslReloadHandler(new DslProperties("", null, null, null, null), null),
            mapper);
    ServerResponse response = handler.delete(deleteRequest("foo", "/api/dsl/drafts/foo"));
    assertThat(response.statusCode().value()).isEqualTo(409);
  }

  @Test
  void deleteLeavesPublishedJsonUntouched() throws Exception {
    handler.save(postRequest("/api/dsl/drafts/foo/save"));
    handler.publish(postRequest("/api/dsl/drafts/foo/publish"));
    Path draft = sourceDir.resolve(".workbench/drafts/foo.json");
    Path published = sourceDir.resolve(".workbench/published/foo.json");
    assertThat(draft).exists();
    assertThat(published).exists();

    ServerResponse response = handler.delete(deleteRequest("foo", "/api/dsl/drafts/foo"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    assertThat(draft).doesNotExist();
    assertThat(published).exists();
  }

  @Test
  void deleteRejectsPathTraversal() throws Exception {
    Path outside = sourceDir.resolveSibling("escapee.json");
    Files.writeString(outside, "{}", java.nio.charset.StandardCharsets.UTF_8);

    ServerResponse response = handler
            .delete(deleteRequest("../escapee", "/api/dsl/drafts/../escapee"));

    assertThat(response.statusCode().value()).isEqualTo(404);
    assertThat(outside).exists();
    Files.deleteIfExists(outside);
  }

  @Test
  void saveReturns400WhenBodyInvalid() throws Exception {
    var req = new MockHttpServletRequest("POST", "/api/dsl/drafts/foo/save");
    req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("name", "foo"));
    req.setContentType("application/json");
    req.setContent("not-json".getBytes());
    ServerResponse response = handler.save(
            ServerRequest.create(req, CONVERTERS));
    assertThat(response.statusCode().value()).isEqualTo(400);
  }

  @Test
  void saveReturns409WhenSourceDirBlank() throws Exception {
    handler = new DslDraftHandler(
            new DslProperties("", null, null, null, null),
            new DslReloadHandler(new DslProperties("", null, null, null, null), null),
            mapper);
    ServerResponse response = handler.save(postRequest("/api/dsl/drafts/foo/save"));
    assertThat(response.statusCode().value()).isEqualTo(409);
  }

  @Test
  void routerFunctionIsRegisteredByDefault() {
    new ApplicationContextRunner()
            .withUserConfiguration(DslDraftTestConfig.class,
                    DslDraftRouterConfiguration.class, DslDraftHandler.class)
            .run(ctx -> assertThat(ctx).hasSingleBean(RouterFunction.class));
  }

  @Test
  void listReturnsEmptyWhenDraftsDirMissing() throws Exception {
    ServerResponse response = handler.list(getRequest("/api/dsl/drafts", null));

    assertThat(response.statusCode().value()).isEqualTo(200);
    Object body = ((org.springframework.web.servlet.function.EntityResponse<?>) response).entity();
    assertThat(body).isInstanceOf(java.util.List.class);
    assertThat((java.util.List<?>) body).isEmpty();
  }

  @Test
  void listReturnsEmptyWhenSourceDirBlank() throws Exception {
    handler = new DslDraftHandler(
            new DslProperties("", null, null, null, null),
            new DslReloadHandler(new DslProperties("", null, null, null, null), null),
            mapper);

    ServerResponse response = handler.list(getRequest("/api/dsl/drafts", null));

    assertThat(response.statusCode().value()).isEqualTo(200);
    Object body = ((org.springframework.web.servlet.function.EntityResponse<?>) response).entity();
    assertThat(body).isInstanceOf(java.util.List.class);
    assertThat((java.util.List<?>) body).isEmpty();
  }

  @Test
  void listReturnsSummariesForSavedDrafts() throws Exception {
    handler.save(postRequest("/api/dsl/drafts/foo/save"));
    handler.save(postRequest("/api/dsl/drafts/bar/save", "bar"));
    handler.publish(postRequest("/api/dsl/drafts/bar/publish", "bar"));

    ServerResponse response = handler.list(getRequest("/api/dsl/drafts", null));

    assertThat(response.statusCode().value()).isEqualTo(200);
    Object body = ((org.springframework.web.servlet.function.EntityResponse<?>) response).entity();
    assertThat(body).isInstanceOf(java.util.List.class);
    @SuppressWarnings("unchecked")
    List<DraftSummary> summaries = (List<DraftSummary>) body;
    assertThat(summaries).hasSize(2);
    assertThat(summaries)
            .extracting(DraftSummary::name)
            .containsExactlyInAnyOrder("foo", "bar");
    assertThat(summaries)
            .allSatisfy(s -> {
              assertThat(s.name()).isNotBlank();
              assertThat(s.updatedAt()).isGreaterThan(0L);
              assertThat(s.status()).isNotBlank();
            });
  }

  @Test
  void listSkipsUnparseableDraftFiles() throws Exception {
    handler.save(postRequest("/api/dsl/drafts/foo/save"));
    Path drafts = sourceDir.resolve(".workbench/drafts");
    Files.writeString(drafts.resolve("garbage.json"), "not-json",
            java.nio.charset.StandardCharsets.UTF_8);

    ServerResponse response = handler.list(getRequest("/api/dsl/drafts", null));

    assertThat(response.statusCode().value()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    List<DraftSummary> summaries = (List<DraftSummary>) ((org.springframework.web.servlet.function.EntityResponse<?>) response)
            .entity();
    assertThat(summaries).hasSize(1);
    assertThat(summaries.get(0).name()).isEqualTo("foo");
  }

  @Test
  void readReturnsDraftPayload() throws Exception {
    handler.save(postRequest("/api/dsl/drafts/foo/save"));

    ServerResponse response = handler
            .read(getRequest("/api/dsl/drafts/foo", Map.of("name", "foo")));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DraftRequest body = (DraftRequest) ((org.springframework.web.servlet.function.EntityResponse<?>) response)
            .entity();
    assertThat(body.name()).isEqualTo("foo");
    assertThat(body.status()).isEqualTo("Draft");
  }

  @Test
  void readReturns404WhenUnknown() throws Exception {
    ServerResponse response = handler
            .read(getRequest("/api/dsl/drafts/missing", Map.of("name", "missing")));

    assertThat(response.statusCode().value()).isEqualTo(404);
  }

  @Test
  void readRejectsPathTraversal() throws Exception {
    Path outside = sourceDir.resolveSibling("escapee.json");
    Files.writeString(outside, "{\"name\":\"escapee\"}", java.nio.charset.StandardCharsets.UTF_8);

    ServerResponse response = handler.read(getRequest("/api/dsl/drafts/..%2Fescapee",
            Map.of("name", "../escapee")));

    assertThat(response.statusCode().value()).isEqualTo(404);
    assertThat(outside).exists();
    Files.deleteIfExists(outside);
  }

  @Test
  void routerFunctionSkippedWhenDisabled() {
    new ApplicationContextRunner()
            .withUserConfiguration(DslDraftTestConfig.class,
                    DslDraftRouterConfiguration.class, DslDraftHandler.class)
            .withPropertyValues("dsl.drafts.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(RouterFunction.class));
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

  private static final class InputStreamHttpMessageConverter
          implements
            HttpMessageConverter<InputStream> {

    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
      return InputStream.class.isAssignableFrom(clazz);
    }

    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
      return false;
    }

    @Override
    public List<MediaType> getSupportedMediaTypes() {
      return List.of(MediaType.ALL);
    }

    @Override
    public InputStream read(Class<? extends InputStream> clazz, HttpInputMessage inputMessage)
            throws IOException {
      return inputMessage.getBody();
    }

    @Override
    public void write(InputStream inputStream, MediaType contentType,
            HttpOutputMessage outputMessage) {
      throw new UnsupportedOperationException();
    }
  }

  @Configuration
  @EnableConfigurationProperties(DslProperties.class)
  static class DslDraftTestConfig {

    @Bean
    DslReloadHandler dslReloadHandler(DslProperties props) {
      return new DslReloadHandler(props, null);
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }

  @Test
  void publishSurfacesCompileDiagnosticsWhenReloadFails() throws Exception {
    Files.writeString(sourceDir.resolve("Broken.java"),
            "this is not valid Java at all; { class Broken { ???");

    ServerResponse response = handler.publish(postRequest("/api/dsl/drafts/foo/publish"));
    assertThat(response.statusCode().value()).isEqualTo(200);

    Object entity = ((org.springframework.web.servlet.function.EntityResponse<?>) response)
            .entity();
    assertThat(entity).isInstanceOf(DraftResponse.class);
    DraftResponse draft = (DraftResponse) entity;
    assertThat(draft.reloaded()).isFalse();
    assertThat(draft.reloadError()).isNotBlank();
    assertThat(draft.diagnostics()).isNotEmpty();
    assertThat(draft.diagnostics().get(0).file()).contains("Broken.java");
    assertThat(draft.diagnostics().get(0).message()).isNotBlank();
    assertThat(draft.diagnostics().get(0).severity()).isEqualTo("error");
  }
}
