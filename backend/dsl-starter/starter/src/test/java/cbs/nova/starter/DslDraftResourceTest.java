package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.DslDraftRouterConfiguration;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.DslDraftHandler;
import cbs.nova.starter.controller.DslReloadHandler;
import cbs.nova.starter.model.VcsModels.DefinitionHistoryEntry;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import cbs.nova.starter.model.VcsModels.DraftResponse;
import cbs.nova.starter.model.VcsModels.DraftSummary;
import cbs.nova.starter.service.DslDefinitionHistoryService;
import cbs.nova.starter.service.DslDefinitionBundleService;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.Optional;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import tools.jackson.databind.ObjectMapper;

class DslDraftResourceTest {

  private DslDraftHandler handler;
  private DslProperties props;
  private Path sourceDir;
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() throws IOException {
    sourceDir = Files.createTempDirectory("dsl-draft-test-");
    props = new DslProperties();
    props.setSourceDir(sourceDir.toString());
    handler = new DslDraftHandler(props, new DslReloadHandler(props, null),
            new DslDefinitionHistoryService(props, mapper), mapper,
            new DslDefinitionBundleService(mapper, Optional.empty()));
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
    return postRequest(path, name, "1");
  }

  private static ServerRequest postRequest(String path, String name, String version) {
    var req = new MockHttpServletRequest("POST", path);
    req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("name", name));
    req.setContentType("application/json");
    req.setContent(
            ("{\"name\":\"" + name
                    + "\",\"type\":\"process\",\"status\":\"Draft\",\"version\":\"" + version
                    + "\"}")
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
    DslProperties blank = new DslProperties();
    blank.setSourceDir("");
    handler = new DslDraftHandler(
            blank,
            new DslReloadHandler(blank, null),
            new DslDefinitionHistoryService(blank, mapper),
            mapper,
            new DslDefinitionBundleService(mapper, Optional.empty()));
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
    DslProperties blank = new DslProperties();
    blank.setSourceDir("");
    handler = new DslDraftHandler(
            blank,
            new DslReloadHandler(blank, null),
            new DslDefinitionHistoryService(blank, mapper),
            mapper,
            new DslDefinitionBundleService(mapper, Optional.empty()));
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
    DslProperties blank = new DslProperties();
    blank.setSourceDir("");
    handler = new DslDraftHandler(
            blank,
            new DslReloadHandler(blank, null),
            new DslDefinitionHistoryService(blank, mapper),
            mapper,
            new DslDefinitionBundleService(mapper, Optional.empty()));

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

  @Test
  void publishSnapshotsPreviousPublishedPayload() throws Exception {
    handler.publish(postRequest("/api/dsl/drafts/X/publish", "X", "A"));
    Thread.sleep(2);
    handler.publish(postRequest("/api/dsl/drafts/X/publish", "X", "B"));

    Path historyDir = sourceDir.resolve(".workbench/history/X");
    assertThat(historyDir).isDirectory();
    List<Path> files;
    try (Stream<Path> s = Files.list(historyDir)) {
      files = s.filter(Files::isRegularFile).toList();
    }
    assertThat(files).hasSize(1);
    DraftRequest snapshot = mapper.readValue(files.get(0).toFile(), DraftRequest.class);
    assertThat(snapshot.name()).isEqualTo("X");
    assertThat(snapshot.status()).isEqualTo("Published");
    assertThat(snapshot.version()).isEqualTo("A");
  }

  @Test
  void firstPublishCreatesNoHistoryEntry() throws Exception {
    handler.publish(postRequest("/api/dsl/drafts/X/publish", "X", "1"));

    Path historyDir = sourceDir.resolve(".workbench/history/X");
    assertThat(historyDir).doesNotExist();
  }

  @Test
  void historyLimitPrunesOldSnapshots() throws Exception {
    props.getDrafts().setHistoryLimit(2);
    for (int i = 1; i <= 4; i++) {
      handler.publish(postRequest("/api/dsl/drafts/X/publish", "X", String.valueOf(i)));
      Thread.sleep(2);
    }

    Path historyDir = sourceDir.resolve(".workbench/history/X");
    assertThat(historyDir).isDirectory();
    List<String> timestamps;
    try (Stream<Path> s = Files.list(historyDir)) {
      timestamps = s.filter(Files::isRegularFile)
              .map(p -> p.getFileName().toString())
              .sorted(Comparator.reverseOrder())
              .toList();
    }
    assertThat(timestamps).hasSize(2);
    for (String timestamp : timestamps) {
      assertThat(timestamp).endsWith(".json");
    }
  }

  @Test
  void historyReturnsNewestFirstEntries() throws Exception {
    handler.publish(postRequest("/api/dsl/drafts/X/publish", "X", "1"));
    Thread.sleep(2);
    handler.publish(postRequest("/api/dsl/drafts/X/publish", "X", "2"));

    ServerResponse response = handler.history(getRequest("/api/dsl/drafts/X/history",
            Map.of("name", "X")));

    assertThat(response.statusCode().value()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    List<DefinitionHistoryEntry> entries = (List<DefinitionHistoryEntry>) ((org.springframework.web.servlet.function.EntityResponse<?>) response)
            .entity();
    assertThat(entries).hasSize(1);
    DefinitionHistoryEntry entry = entries.get(0);
    assertThat(entry.timestamp()).isNotBlank();
    assertThat(entry.timestampMillis()).isGreaterThan(0L);
    assertThat(entry.sizeBytes()).isGreaterThan(0L);
    assertThat(entry.lastModifiedMillis()).isGreaterThan(0L);
  }

  @Test
  void restoreRollsBackPublishedMetadata() throws Exception {
    handler.publish(postRequest("/api/dsl/drafts/X/publish", "X", "A"));
    Thread.sleep(2);
    handler.publish(postRequest("/api/dsl/drafts/X/publish", "X", "B"));

    List<DefinitionHistoryEntry> entries = historyEntries("X");
    assertThat(entries).hasSize(1);
    String timestamp = entries.get(0).timestamp();

    Thread.sleep(2);
    ServerResponse response = handler.restore(getRequest(
            "/api/dsl/drafts/X/history/" + timestamp + "/restore",
            Map.of("name", "X", "timestamp", timestamp)));

    assertThat(response.statusCode().value()).isEqualTo(200);
    Path published = sourceDir.resolve(".workbench/published/X.json");
    DraftRequest current = mapper.readValue(published.toFile(), DraftRequest.class);
    assertThat(current.version()).isEqualTo("A");
    assertThat(current.status()).isEqualTo("Published");

    List<DefinitionHistoryEntry> after = historyEntries("X");
    assertThat(after).hasSize(2);
  }

  @Test
  void restoreUnknownTimestampReturns404() throws Exception {
    handler.publish(postRequest("/api/dsl/drafts/X/publish", "X", "A"));

    ServerResponse response = handler.restore(getRequest(
            "/api/dsl/drafts/X/history/9999999999999/restore",
            Map.of("name", "X", "timestamp", "9999999999999")));

    assertThat(response.statusCode().value()).isEqualTo(404);
    Path published = sourceDir.resolve(".workbench/published/X.json");
    DraftRequest current = mapper.readValue(published.toFile(), DraftRequest.class);
    assertThat(current.version()).isEqualTo("A");
  }

  @Test
  void restoreNonNumericTimestampReturns404() throws Exception {
    handler.publish(postRequest("/api/dsl/drafts/X/publish", "X", "A"));

    ServerResponse response = handler.restore(getRequest(
            "/api/dsl/drafts/X/history/evil/restore",
            Map.of("name", "X", "timestamp", "evil")));

    assertThat(response.statusCode().value()).isEqualTo(404);
    Path published = sourceDir.resolve(".workbench/published/X.json");
    DraftRequest current = mapper.readValue(published.toFile(), DraftRequest.class);
    assertThat(current.version()).isEqualTo("A");
  }

  @Test
  void historySanitizesTraversalName() throws Exception {
    ServerResponse response = handler.history(getRequest("/api/dsl/drafts/../../etc/history",
            Map.of("name", "../../etc")));

    assertThat(response.statusCode().value()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    List<DefinitionHistoryEntry> entries = (List<DefinitionHistoryEntry>) ((org.springframework.web.servlet.function.EntityResponse<?>) response)
            .entity();
    assertThat(entries).isEmpty();
    Path escaped = sourceDir.resolve(".workbench/history/").toAbsolutePath().getParent().getParent()
            .resolve("etc");
    assertThat(escaped).doesNotExist();
  }

  @Test
  void restoreSanitizesTraversalName() throws Exception {
    handler.publish(postRequest("/api/dsl/drafts/X/publish", "X", "A"));

    ServerResponse response = handler.restore(getRequest(
            "/api/dsl/drafts/../../etc/history/123/restore",
            Map.of("name", "../../etc", "timestamp", "123")));

    assertThat(response.statusCode().value()).isEqualTo(404);
  }

  @Test
  void historyReturns409WhenSourceDirBlank() throws Exception {
    DslProperties blank = new DslProperties();
    blank.setSourceDir("");
    handler = new DslDraftHandler(
            blank,
            new DslReloadHandler(blank, null),
            new DslDefinitionHistoryService(blank, mapper),
            mapper,
            new DslDefinitionBundleService(mapper, Optional.empty()));

    ServerResponse response = handler.history(getRequest("/api/dsl/drafts/X/history",
            Map.of("name", "X")));

    assertThat(response.statusCode().value()).isEqualTo(409);
  }

  @Test
  void restoreReturns409WhenSourceDirBlank() throws Exception {
    DslProperties blank = new DslProperties();
    blank.setSourceDir("");
    handler = new DslDraftHandler(
            blank,
            new DslReloadHandler(blank, null),
            new DslDefinitionHistoryService(blank, mapper),
            mapper,
            new DslDefinitionBundleService(mapper, Optional.empty()));

    ServerResponse response = handler.restore(getRequest(
            "/api/dsl/drafts/X/history/123/restore",
            Map.of("name", "X", "timestamp", "123")));

    assertThat(response.statusCode().value()).isEqualTo(409);
  }

  private List<DefinitionHistoryEntry> historyEntries(String name) throws Exception {
    ServerResponse response = handler.history(getRequest("/api/dsl/drafts/" + name + "/history",
            Map.of("name", name)));
    @SuppressWarnings("unchecked")
    List<DefinitionHistoryEntry> entries = (List<DefinitionHistoryEntry>) ((org.springframework.web.servlet.function.EntityResponse<?>) response)
            .entity();
    return entries;
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
    DslDefinitionHistoryService dslDefinitionHistoryService(DslProperties props,
            ObjectMapper mapper) {
      return new DslDefinitionHistoryService(props, mapper);
    }

    @Bean
    DslDefinitionBundleService dslDefinitionBundleService(ObjectMapper mapper) {
      return new DslDefinitionBundleService(mapper, java.util.Optional.empty());
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }

}
