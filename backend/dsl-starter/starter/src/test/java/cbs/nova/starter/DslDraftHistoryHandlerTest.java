package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.DslDraftHistoryHandler;
import cbs.nova.starter.controller.DslDraftSupport;
import cbs.nova.starter.exception.BuilderApiException;
import cbs.nova.starter.model.DslFileModels.FileContentResponse;
import cbs.nova.starter.model.VcsModels.DefinitionHistoryEntry;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import cbs.nova.starter.model.VcsModels.DraftResponse;
import cbs.nova.starter.model.VcsModels.HistoryDiffResponse;
import cbs.nova.starter.model.VcsModels.LogEntry;
import cbs.nova.starter.service.DslSourcePathResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.EntityResponse;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Tests for {@link DslDraftHistoryHandler}. History-only cases lifted from the old
 * {@code DslDraftResourceTest} / {@code DslDraftGitResourceTest} when the handler split happened
 * (T731).
 */
class DslDraftHistoryHandlerTest {

  private Path sourceDir;
  private DslProperties props;
  private DslDraftHistoryHandler historyHandler;
  private DslBuilderClient client;
  private DslSourcePathResolver sourcePathResolver;

  @BeforeEach
  void setUp() throws IOException {
    sourceDir = Files.createTempDirectory("dsl-draft-history-test-");
    props = DslProperties.builder().sourceDir(sourceDir.toString()).build();
    client = mock(DslBuilderClient.class);
    sourcePathResolver = mock(DslSourcePathResolver.class);
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));
    DslDraftSupport support = new DslDraftSupport(props, providerOf(client),
            null, null, providerOfBean(sourcePathResolver));
    historyHandler = new DslDraftHistoryHandler(support);
  }

  @AfterEach
  void tearDown() throws IOException {
    if (sourceDir != null && Files.exists(sourceDir)) {
      try (var stream = Files.walk(sourceDir).sorted((a, b) -> -a.compareTo(b))) {
        stream.forEach(p -> {
          try {
            Files.deleteIfExists(p);
          } catch (IOException ignored) {
          }
        });
      }
    }
  }

  @Test
  void historyIsGitOnlyInBuilderMode() throws Exception {
    when(client.vcsLog("dsl/FooDsl.java", 50)).thenReturn(List.of(
            new LogEntry("abc123", 1L, "alice", "x")));

    ServerResponse response = historyHandler.history(getRequest("/api/dsl/drafts/foo/history",
            Map.of("name", "foo")));

    assertThat(response.statusCode().value()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    List<DefinitionHistoryEntry> body = (List<DefinitionHistoryEntry>) ((EntityResponse<?>) response)
            .entity();
    assertThat(body).hasSize(1);
    assertThat(body.get(0).timestamp()).isEqualTo("abc123");
  }

  @Test
  void historyMapsLogEntriesToDefinitionHistoryEntries() throws Exception {
    when(client.vcsLog("dsl/FooDsl.java", 50)).thenReturn(List.of(
            new LogEntry("abc1234", 1000L, "alice", "Publish foo"),
            new LogEntry("def5678", 2000L, "bob", "Publish foo v2")));

    ServerResponse response = historyHandler.history(getRequest("/api/dsl/drafts/foo/history",
            Map.of("name", "foo")));

    assertThat(response.statusCode().value()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    List<DefinitionHistoryEntry> body = (List<DefinitionHistoryEntry>) ((EntityResponse<?>) response)
            .entity();
    assertThat(body).hasSize(2);
    assertThat(body.get(0).timestamp()).isEqualTo("abc1234");
    assertThat(body.get(0).timestampMillis()).isEqualTo(1000L);
    assertThat(body.get(0).sizeBytes()).isEqualTo(-1L);
  }

  @Test
  void historyEntryReturnsSourceAtCommit() throws Exception {
    when(client.vcsShow("dsl/FooDsl.java", "abc1234")).thenReturn("class Foo {}");

    ServerResponse response = historyHandler.historyEntry(getRequest(
            "/api/dsl/drafts/foo/history/abc1234", Map.of("name", "foo", "timestamp", "abc1234")));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DraftRequest body = (DraftRequest) ((EntityResponse<?>) response).entity();
    assertThat(body.status()).isEqualTo("History");
    assertThat(body.source()).isEqualTo("class Foo {}");
  }

  @Test
  void historyEntryReturns404WhenShowNotFound() throws Exception {
    when(client.vcsShow("dsl/FooDsl.java", "abc1234")).thenThrow(new BuilderApiException(
            HttpStatus.NOT_FOUND, "NOT_FOUND", "no such commit"));

    ServerResponse response = historyHandler.historyEntry(getRequest(
            "/api/dsl/drafts/foo/history/abc1234", Map.of("name", "foo", "timestamp", "abc1234")));

    assertThat(response.statusCode().value()).isEqualTo(404);
  }

  @Test
  void historyDiffReturnsDiff() throws Exception {
    when(client.vcsShow("dsl/FooDsl.java", "abc1234")).thenReturn("class Foo { one }");
    when(client.readFile("dsl/FooDsl.java")).thenReturn(
            new FileContentResponse("dsl/FooDsl.java", "class Foo { two }", 0L));

    ServerResponse response = historyHandler.historyDiff(getRequest(
            "/api/dsl/drafts/foo/history/abc1234/diff",
            Map.of("name", "foo", "timestamp", "abc1234")));

    assertThat(response.statusCode().value()).isEqualTo(200);
    HistoryDiffResponse body = (HistoryDiffResponse) ((EntityResponse<?>) response).entity();
    assertThat(body.before()).isEqualTo("class Foo { one }");
    assertThat(body.after()).isEqualTo("class Foo { two }");
  }

  @Test
  void restoreWritesCommitSourceAsDraft() throws Exception {
    when(client.vcsShow("dsl/FooDsl.java", "abc1234")).thenReturn("class Foo {}");

    ServerResponse response = historyHandler.restore(postRequest(
            "/api/dsl/drafts/foo/history/abc1234/restore",
            Map.of("name", "foo", "timestamp", "abc1234")));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DraftResponse body = (DraftResponse) ((EntityResponse<?>) response).entity();
    assertThat(body.status()).isEqualTo("Draft");
    verify(client).stageWrite("dsl/FooDsl.java", "class Foo {}");
    verify(client).flushFiles();
  }

  @Test
  void restoreReturns404ForUnknownCommit() throws Exception {
    when(client.vcsShow("dsl/FooDsl.java", "abc1234")).thenThrow(new BuilderApiException(
            HttpStatus.NOT_FOUND, "NOT_FOUND", "no such commit"));

    ServerResponse response = historyHandler.restore(postRequest(
            "/api/dsl/drafts/foo/history/abc1234/restore",
            Map.of("name", "foo", "timestamp", "abc1234")));

    assertThat(response.statusCode().value()).isEqualTo(404);
  }

  @Test
  void historyNoLongerFallsBackToNumericSnapshot() throws Exception {
    when(client.vcsShow("dsl/FooDsl.java", "1727150000000")).thenReturn("class Old {}");

    ServerResponse response = historyHandler.historyEntry(getRequest(
            "/api/dsl/drafts/foo/history/1727150000000",
            Map.of("name", "foo", "timestamp", "1727150000000")));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DraftRequest body = (DraftRequest) ((EntityResponse<?>) response).entity();
    assertThat(body.source()).isEqualTo("class Old {}");
  }

  @Test
  void commitsReturnsLogEntries() throws Exception {
    when(client.vcsLog("dsl/FooDsl.java", 20)).thenReturn(List.of(
            new LogEntry("abc123", 1, "alice", "Publish foo")));

    ServerResponse response = historyHandler.commits(getRequest("/api/dsl/drafts/foo/commits",
            Map.of("name", "foo")));

    assertThat(response.statusCode().value()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    List<LogEntry> body = (List<LogEntry>) ((EntityResponse<?>) response).entity();
    assertThat(body).hasSize(1);
    assertThat(body.get(0).commitId()).isEqualTo("abc123");
  }

  @Test
  void commitsRespectsLimitParam() throws Exception {
    when(client.vcsLog("dsl/FooDsl.java", 5)).thenReturn(List.of());

    historyHandler.commits(getRequestWithQuery("/api/dsl/drafts/foo/commits", "limit=5",
            Map.of("name", "foo")));

    verify(client).vcsLog("dsl/FooDsl.java", 5);
  }

  private static ServerRequest getRequest(String path, Map<String, String> pathVariables) {
    var req = new MockHttpServletRequest("GET", path);
    if (pathVariables != null && !pathVariables.isEmpty()) {
      req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, pathVariables);
    }
    return ServerRequest.create(req, CONVERTERS);
  }

  private static ServerRequest postRequest(String path, Map<String, String> pathVariables) {
    var req = new MockHttpServletRequest("POST", path);
    req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, pathVariables);
    req.setContentType("application/json");
    req.setContent("{}".getBytes(StandardCharsets.UTF_8));
    return ServerRequest.create(req, CONVERTERS);
  }

  private static ServerRequest getRequestWithQuery(String path, String query,
          Map<String, String> pathVariables) {
    var req = new MockHttpServletRequest("GET", path + "?" + query);
    if (pathVariables != null && !pathVariables.isEmpty()) {
      req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, pathVariables);
    }
    req.setQueryString(query);
    for (String pair : query.split("&")) {
      String[] kv = pair.split("=", 2);
      if (kv.length == 2) {
        req.addParameter(kv[0], kv[1]);
      }
    }
    return ServerRequest.create(req, CONVERTERS);
  }

  private static final List<HttpMessageConverter<?>> CONVERTERS = List
          .of(new ByteArrayHttpMessageConverter(),
                  new StringHttpMessageConverter());

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> providerOf(T bean) {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(bean);
    return provider;
  }

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> providerOfBean(T bean) {
    return providerOf(bean);
  }
}
