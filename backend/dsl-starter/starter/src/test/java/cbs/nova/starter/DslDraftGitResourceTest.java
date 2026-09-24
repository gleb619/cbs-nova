package cbs.nova.starter;

import static cbs.nova.starter.BuilderClientTestSupport.emptyZip;
import static cbs.nova.starter.BuilderClientTestSupport.providerOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.model.LoadResult;
import cbs.nova.dsl.utils.DefinitionLoader;
import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.DslDraftHandler;
import cbs.nova.starter.controller.DslReloadHandler;
import cbs.nova.starter.exception.BuilderApiException;
import cbs.nova.starter.model.CompileModels.CompileRequest;
import cbs.nova.starter.model.CompileModels.CompileResult;
import cbs.nova.starter.model.VcsModels.CommitRequest;
import cbs.nova.starter.model.VcsModels.CommitResult;
import cbs.nova.starter.model.VcsModels.DiscardRequest;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import cbs.nova.starter.model.VcsModels.DraftResponse;
import cbs.nova.starter.model.VcsModels.LogEntry;
import cbs.nova.starter.service.DslDefinitionBundleService;
import cbs.nova.starter.service.DslDefinitionHistoryService;
import cbs.nova.starter.service.DslGitStatusResolver;
import cbs.nova.starter.service.DslSourcePathResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.EntityResponse;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

class DslDraftGitResourceTest {

  private DslDraftHandler handler;
  private DslBuilderClient client;
  private DslSourcePathResolver sourcePathResolver;
  private DslGitStatusResolver gitStatusResolver;
  private DslProperties props;
  private Path sourceDir;
  private final ObjectMapper mapper = new ObjectMapper();
  private GlobalManager previousGlobalManager;

  @BeforeEach
  void setUp() throws IOException {
    previousGlobalManager = GlobalManager.globalManager();
    sourceDir = Files.createTempDirectory("dsl-draft-git-test-");
    props = DslProperties.builder().sourceDir(sourceDir.toString()).build();
    client = mock(DslBuilderClient.class);
    sourcePathResolver = mock(DslSourcePathResolver.class);
    gitStatusResolver = mock(DslGitStatusResolver.class);
    handler = builderHandler(client);
  }

  @AfterEach
  void tearDown() throws IOException {
    GlobalManager.globalManager().replaceGlobalManager(previousGlobalManager);
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
  void publishCommitsWhenReloadSucceedsAndPathIsDirty() throws Exception {
    when(client.publishDraft(eq("foo"), any())).thenReturn(
            new DraftResponse("foo", "Published", "/remote/.workbench/published/foo.json", false,
                    LoadResult.empty(), null, null, null, null));
    when(client.compile(any())).thenReturn(new CompileResult("s-1", true, List.of(), List.of(), 1));
    when(client.downloadZip(anyString())).thenReturn(emptyZip());
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));
    var status = DslGitStatusResolver.RepoStatus.of(sourceDir,
            Map.of("dsl/FooDsl.java", DslGitStatusResolver.ChangeType.MODIFIED));
    when(gitStatusResolver.status(sourceDir)).thenReturn(Optional.of(status));
    when(client.commit(any()))
            .thenReturn(new CommitResult("abc123", List.of("dsl/FooDsl.java"), 42));

    ServerResponse response = handler.publish(postRequest("/api/dsl/drafts/foo/publish", "foo"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DraftResponse body = (DraftResponse) ((EntityResponse<?>) response).entity();
    assertThat(body.reloaded()).isTrue();
    assertThat(body.commitId()).isEqualTo("abc123");
    verify(client).commit(argThatCommit("dsl/FooDsl.java", "Publish foo"));
  }

  @Test
  void publishDoesNotCommitWhenReloadFails() throws Exception {
    Files.writeString(sourceDir.resolve("Broken.java"), "this is not valid Java");
    when(client.publishDraft(eq("foo"), any())).thenReturn(
            new DraftResponse("foo", "Published", "/remote/.workbench/published/foo.json", false,
                    LoadResult.empty(), null, null, null, null));
    when(client.compile(any())).thenReturn(new CompileResult("s-1", false, List.of(),
            List.of("bad syntax"), 1));
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));

    ServerResponse response = handler.publish(postRequest("/api/dsl/drafts/foo/publish", "foo"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DraftResponse body = (DraftResponse) ((EntityResponse<?>) response).entity();
    assertThat(body.reloaded()).isFalse();
    assertThat(body.commitId()).isNull();
    verify(client, never()).commit(any());
  }

  @Test
  void publishDoesNotCommitWhenPathIsClean() throws Exception {
    when(client.publishDraft(eq("foo"), any())).thenReturn(
            new DraftResponse("foo", "Published", "/remote/.workbench/published/foo.json", false,
                    LoadResult.empty(), null, null, null, null));
    when(client.compile(any())).thenReturn(new CompileResult("s-1", true, List.of(), List.of(), 1));
    when(client.downloadZip(anyString())).thenReturn(emptyZip());
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));
    var status = DslGitStatusResolver.RepoStatus.of(sourceDir, Map.of());
    when(gitStatusResolver.status(sourceDir)).thenReturn(Optional.of(status));

    ServerResponse response = handler.publish(postRequest("/api/dsl/drafts/foo/publish", "foo"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DraftResponse body = (DraftResponse) ((EntityResponse<?>) response).entity();
    assertThat(body.reloaded()).isTrue();
    assertThat(body.commitId()).isNull();
    verify(client, never()).commit(any());
  }

  @Test
  void publishReturns200WithNullCommitIdWhenCommitThrows() throws Exception {
    when(client.publishDraft(eq("foo"), any())).thenReturn(
            new DraftResponse("foo", "Published", "/remote/.workbench/published/foo.json", false,
                    LoadResult.empty(), null, null, null, null));
    when(client.compile(any())).thenReturn(new CompileResult("s-1", true, List.of(), List.of(), 1));
    when(client.downloadZip(anyString())).thenReturn(emptyZip());
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));
    var status = DslGitStatusResolver.RepoStatus.of(sourceDir,
            Map.of("dsl/FooDsl.java", DslGitStatusResolver.ChangeType.MODIFIED));
    when(gitStatusResolver.status(sourceDir)).thenReturn(Optional.of(status));
    when(client.commit(any())).thenThrow(new BuilderApiException(
            HttpStatus.INTERNAL_SERVER_ERROR, "COMMIT_FAILED", "git refused"));

    ServerResponse response = handler.publish(postRequest("/api/dsl/drafts/foo/publish", "foo"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DraftResponse body = (DraftResponse) ((EntityResponse<?>) response).entity();
    assertThat(body.reloaded()).isTrue();
    assertThat(body.commitId()).isNull();
  }

  @Test
  void discardChecksOutFileAndDeletesLegacyMarker() throws Exception {
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));
    when(client.discard(any())).thenReturn(
            new cbs.nova.starter.model.VcsModels.DiscardResult(List.of("dsl/FooDsl.java")));
    when(client.deleteDraft("foo")).thenThrow(new BuilderApiException(
            HttpStatus.NOT_FOUND, "NOT_FOUND", "no marker"));

    ServerResponse response = handler.discard(postRequest("/api/dsl/drafts/foo/discard", "foo"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DraftResponse body = (DraftResponse) ((EntityResponse<?>) response).entity();
    assertThat(body.status()).isEqualTo("Discarded");
    assertThat(body.location()).isEqualTo("dsl/FooDsl.java");
    assertThat(body.reloaded()).isFalse();
    verify(client).discard(new DiscardRequest(List.of("dsl/FooDsl.java")));
    verify(client).deleteDraft("foo");
  }

  @Test
  void discardPropagatesMarkerDeleteErrorsAbove404() {
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));
    when(client.discard(any())).thenReturn(
            new cbs.nova.starter.model.VcsModels.DiscardResult(List.of("dsl/FooDsl.java")));
    when(client.deleteDraft("foo")).thenThrow(new BuilderApiException(
            HttpStatus.INTERNAL_SERVER_ERROR, "ERROR", "boom"));

    assertThatThrownBy(() -> handler.discard(postRequest("/api/dsl/drafts/foo/discard", "foo")))
            .isInstanceOf(BuilderApiException.class)
            .hasMessageContaining("boom");
  }

  @Test
  void commitsReturnsLogEntries() throws Exception {
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));
    when(client.vcsLog("dsl/FooDsl.java", 20)).thenReturn(List.of(
            new LogEntry("abc123", 1, "alice", "Publish foo")));

    ServerResponse response = handler.commits(getRequest("/api/dsl/drafts/foo/commits",
            Map.of("name", "foo")));

    assertThat(response.statusCode().value()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    List<LogEntry> body = (List<LogEntry>) ((EntityResponse<?>) response).entity();
    assertThat(body).hasSize(1);
    assertThat(body.get(0).commitId()).isEqualTo("abc123");
  }

  @Test
  void commitsRespectsLimitParam() throws Exception {
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));
    when(client.vcsLog("dsl/FooDsl.java", 5)).thenReturn(List.of());

    handler.commits(getRequestWithQuery("/api/dsl/drafts/foo/commits", "limit=5",
            Map.of("name", "foo")));

    verify(client).vcsLog("dsl/FooDsl.java", 5);
  }

  @Test
  void discardRequiresBuilder() throws Exception {
    handler = localOnlyHandler();
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));

    ServerResponse response = handler.discard(postRequest("/api/dsl/drafts/foo/discard", "foo"));

    assertThat(response.statusCode().value()).isEqualTo(409);
    cbs.nova.dsl.model.ErrorResponse body = (cbs.nova.dsl.model.ErrorResponse) ((EntityResponse<?>) response)
            .entity();
    assertThat(body.code()).isEqualTo("GIT_REQUIRES_BUILDER");
  }

  @Test
  void commitsRequiresBuilder() {
    handler = localOnlyHandler();
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));

    ServerResponse response = handler.commits(getRequest("/api/dsl/drafts/foo/commits",
            Map.of("name", "foo")));

    assertThat(response.statusCode().value()).isEqualTo(409);
    cbs.nova.dsl.model.ErrorResponse body = (cbs.nova.dsl.model.ErrorResponse) ((EntityResponse<?>) response)
            .entity();
    assertThat(body.code()).isEqualTo("GIT_REQUIRES_BUILDER");
  }

  @Test
  void discardReturns404ForUnknownDefinition() throws Exception {
    when(sourcePathResolver.relativePath("unknown")).thenReturn(Optional.empty());

    ServerResponse response = handler
            .discard(postRequest("/api/dsl/drafts/unknown/discard", "unknown"));

    assertThat(response.statusCode().value()).isEqualTo(404);
  }

  @Test
  void commitsReturns404ForUnknownDefinition() {
    when(sourcePathResolver.relativePath("unknown")).thenReturn(Optional.empty());

    ServerResponse response = handler.commits(getRequest("/api/dsl/drafts/unknown/commits",
            Map.of("name", "unknown")));

    assertThat(response.statusCode().value()).isEqualTo(404);
  }

  private DslDraftHandler builderHandler(DslBuilderClient client) {
    return new DslDraftHandler(props,
            new DslReloadHandler(props, new DefinitionLoader(), null, null, providerOf(client),
                    null, null, null),
            new DslDefinitionHistoryService(props, mapper), mapper,
            new DslDefinitionBundleService(mapper, Optional.empty(),
                    DslProperties.bundleServiceDefaults()),
            null, providerOf(client), null, null, null,
            providerOfBean(sourcePathResolver), providerOfBean(gitStatusResolver));
  }

  private DslDraftHandler localOnlyHandler() {
    return new DslDraftHandler(props,
            new DslReloadHandler(props, null, null, null, null, null, null, null),
            new DslDefinitionHistoryService(props, mapper), mapper,
            new DslDefinitionBundleService(mapper, Optional.empty(),
                    DslProperties.bundleServiceDefaults()),
            null, null, null, null, null,
            providerOfBean(sourcePathResolver), providerOfBean(gitStatusResolver));
  }

  private static <T> ObjectProvider<T> providerOfBean(T bean) {
    @SuppressWarnings("unchecked")
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(bean);
    return provider;
  }

  private static CommitRequest argThatCommit(String path, String message) {
    return org.mockito.ArgumentMatchers.argThat(req -> req != null
            && req.paths().equals(List.of(path)) && message.equals(req.message()));
  }

  private static ServerRequest getRequestWithQuery(String path, String queryString,
          Map<String, String> pathVariables) {
    var req = new MockHttpServletRequest("GET", path);
    req.setQueryString(queryString);
    for (String pair : queryString.split("&")) {
      int eq = pair.indexOf('=');
      if (eq > 0) {
        req.addParameter(pair.substring(0, eq), pair.substring(eq + 1));
      }
    }
    if (pathVariables != null && !pathVariables.isEmpty()) {
      req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
              pathVariables);
    }
    return ServerRequest.create(req, DslDraftResourceTest.converters());
  }

  private static ServerRequest postRequest(String path, String name) {
    var req = new MockHttpServletRequest("POST", path);
    req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
            Map.of("name", name));
    req.setContentType("application/json");
    req.setContent(("{\"name\":\"" + name
            + "\",\"type\":\"process\",\"status\":\"Draft\",\"version\":\"1\"}").getBytes());
    return ServerRequest.create(req, DslDraftResourceTest.converters());
  }

  private static ServerRequest getRequest(String path, Map<String, String> pathVariables) {
    var req = new MockHttpServletRequest("GET", path);
    if (pathVariables != null && !pathVariables.isEmpty()) {
      req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
              pathVariables);
    }
    return ServerRequest.create(req, DslDraftResourceTest.converters());
  }
}
