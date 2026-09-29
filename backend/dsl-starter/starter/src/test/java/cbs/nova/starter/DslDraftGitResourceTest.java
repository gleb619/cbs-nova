package cbs.nova.starter;

import static cbs.nova.starter.BuilderClientTestSupport.emptyZip;
import static cbs.nova.starter.BuilderClientTestSupport.providerOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import cbs.nova.starter.controller.DslDraftSupport;
import cbs.nova.starter.controller.DslReloadHandler;
import cbs.nova.starter.exception.BuilderApiException;
import cbs.nova.starter.model.CompileModels.CompileResult;
import cbs.nova.starter.model.VcsModels.CommitRequest;
import cbs.nova.starter.model.VcsModels.CommitResult;
import cbs.nova.starter.model.VcsModels.DiscardRequest;
import cbs.nova.starter.model.VcsModels.DiscardResult;
import cbs.nova.starter.model.VcsModels.DraftResponse;
import cbs.nova.starter.service.DslDefinitionBundleService;
import cbs.nova.starter.service.DslDefinitionStatusResolver;
import cbs.nova.starter.service.DslGitStatusResolver;
import cbs.nova.starter.service.DslSourcePathResolver;
import cbs.nova.dsl.vcs.ChangeType;
import cbs.nova.dsl.vcs.RepoStatus;
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
import org.mockito.ArgumentMatchers;
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
import tools.jackson.databind.ObjectMapper;

class DslDraftGitResourceTest {

  private DslDraftHandler handler;
  private DslBuilderClient client;
  private DslSourcePathResolver sourcePathResolver;
  private DslGitStatusResolver gitStatusResolver;
  private DslDefinitionStatusResolver statusResolver;
  private DslProperties props;
  private Path sourceDir;
  private final ObjectMapper mapper = new ObjectMapper();
  private GlobalManager previousGlobalManager;

  @BeforeEach
  void setUp() throws IOException {
    previousGlobalManager = GlobalManager.globalManager();
    GlobalManager.globalManager().resetForTests();
    sourceDir = Files.createTempDirectory("dsl-draft-git-test-");
    props = DslProperties.builder().sourceDir(sourceDir.toString()).build();
    client = mock(DslBuilderClient.class);
    sourcePathResolver = mock(DslSourcePathResolver.class);
    gitStatusResolver = mock(DslGitStatusResolver.class);
    statusResolver = new DslDefinitionStatusResolver(props, gitStatusResolver, sourcePathResolver);
    handler = builderHandler(client);
  }

  @AfterEach
  void tearDown() throws IOException {
    GlobalManager.globalManager().replaceGlobalManager(previousGlobalManager);
    GlobalManager.globalManager().resetForTests();
    try (var stream = Files.walk(sourceDir).sorted((a, b) -> -a.compareTo(b))) {
      stream.forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
      });
    }
  }

  @Test
  void publishCommitsWhenReloadSucceedsAndPathIsDirty() throws Exception {
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));
    when(client.compile(any())).thenReturn(
            new CompileResult("s-1", true,
                    List.of(), List.of(), 1));
    when(client.downloadZip(anyString())).thenReturn(emptyZip());
    when(gitStatusResolver.status(sourceDir)).thenReturn(Optional.of(
            RepoStatus.of(sourceDir, Map.of("dsl/FooDsl.java", ChangeType.MODIFIED))));
    when(client.commit(any())).thenReturn(
            new CommitResult("abc123", List.of("dsl/FooDsl.java"), 42, null, null));

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
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));
    when(client.compile(any())).thenReturn(
            new CompileResult("s-1", false,
                    List.of(), List.of("bad syntax"), 1));

    ServerResponse response = handler.publish(postRequest("/api/dsl/drafts/foo/publish", "foo"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DraftResponse body = (DraftResponse) ((EntityResponse<?>) response).entity();
    assertThat(body.reloaded()).isFalse();
    assertThat(body.commitId()).isNull();
    verify(client, never()).commit(any());
  }

  @Test
  void publishDoesNotCommitWhenPathIsClean() throws Exception {
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));
    when(client.compile(any())).thenReturn(
            new CompileResult("s-1", true,
                    List.of(), List.of(), 1));
    when(client.downloadZip(anyString())).thenReturn(emptyZip());
    when(gitStatusResolver.status(sourceDir))
            .thenReturn(Optional.of(RepoStatus.of(sourceDir, Map.of())));

    ServerResponse response = handler.publish(postRequest("/api/dsl/drafts/foo/publish", "foo"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DraftResponse body = (DraftResponse) ((EntityResponse<?>) response).entity();
    assertThat(body.reloaded()).isTrue();
    assertThat(body.commitId()).isNull();
    verify(client, never()).commit(any());
  }

  @Test
  void publishReturns200WithNullCommitIdWhenCommitThrows() throws Exception {
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));
    when(client.compile(any())).thenReturn(
            new CompileResult("s-1", true,
                    List.of(), List.of(), 1));
    when(client.downloadZip(anyString())).thenReturn(emptyZip());
    when(gitStatusResolver.status(sourceDir)).thenReturn(Optional.of(
            RepoStatus.of(sourceDir, Map.of("dsl/FooDsl.java", ChangeType.MODIFIED))));
    when(client.commit(any())).thenThrow(new BuilderApiException(
            HttpStatus.INTERNAL_SERVER_ERROR, "COMMIT_FAILED", "git refused"));

    ServerResponse response = handler.publish(postRequest("/api/dsl/drafts/foo/publish", "foo"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DraftResponse body = (DraftResponse) ((EntityResponse<?>) response).entity();
    assertThat(body.reloaded()).isTrue();
    assertThat(body.commitId()).isNull();
  }

  @Test
  void discardChecksOutFile() throws Exception {
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));
    when(client.discard(any())).thenReturn(
            new DiscardResult(List.of("dsl/FooDsl.java")));

    ServerResponse response = handler.discard(postRequest("/api/dsl/drafts/foo/discard", "foo"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DraftResponse body = (DraftResponse) ((EntityResponse<?>) response).entity();
    assertThat(body.status()).isEqualTo("Discarded");
    assertThat(body.location()).isEqualTo("dsl/FooDsl.java");
    verify(client).discard(new DiscardRequest(List.of("dsl/FooDsl.java")));
  }

  @Test
  void deleteIsSameAsDiscard() throws Exception {
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));
    when(client.discard(any())).thenReturn(new DiscardResult(List.of("dsl/FooDsl.java")));

    ServerResponse response = handler.delete(deleteRequest("foo"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    verify(client).discard(new DiscardRequest(List.of("dsl/FooDsl.java")));
  }

  private DslDraftHandler builderHandler(DslBuilderClient client) {
    DslDefinitionBundleService bundleService = new DslDefinitionBundleService(mapper,
            Optional.empty(), props, sourcePathResolver, providerOf(client));
    DslDraftSupport support = new DslDraftSupport(props,
            providerOf(client), null, null, providerOfBean(sourcePathResolver));
    return new DslDraftHandler(props,
            new DslReloadHandler(props, new DefinitionLoader(), null, null, providerOf(client),
                    null, null, null),
            mapper, bundleService,
            null, providerOf(client), null, null, null,
            providerOfBean(sourcePathResolver), providerOfBean(gitStatusResolver),
            providerOfBean(statusResolver), support);
  }

  private static CommitRequest argThatCommit(String path, String message) {
    return ArgumentMatchers.argThat(r -> r.paths().equals(List.of(path))
            && r.message().equals(message));
  }

  private static ServerRequest postRequest(String path, String name) {
    var req = new MockHttpServletRequest("POST", path);
    req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("name", name));
    req.setContentType("application/json");
    req.setContent(("{\"name\":\"" + name + "\"}").getBytes(StandardCharsets.UTF_8));
    return ServerRequest.create(req, CONVERTERS);
  }

  private static ServerRequest postRequest(String path, Map<String, String> pathVariables) {
    var req = new MockHttpServletRequest("POST", path);
    req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, pathVariables);
    req.setContentType("application/json");
    req.setContent("{}".getBytes(StandardCharsets.UTF_8));
    return ServerRequest.create(req, CONVERTERS);
  }

  private static ServerRequest deleteRequest(String name) {
    var req = new MockHttpServletRequest("DELETE", "/api/dsl/drafts/" + name);
    req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("name", name));
    return ServerRequest.create(req, CONVERTERS);
  }

  private static final List<HttpMessageConverter<?>> CONVERTERS = List
          .of(new ByteArrayHttpMessageConverter(),
                  new StringHttpMessageConverter());

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> providerOfBean(T bean) {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(bean);
    return provider;
  }
}
