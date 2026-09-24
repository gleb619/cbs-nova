package cbs.nova.starter;

import static cbs.nova.starter.BuilderClientTestSupport.providerOf;
import static cbs.nova.starter.BuilderClientTestSupport.stubLocalCompile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.model.LoadResult;
import cbs.nova.dsl.utils.DefinitionLoader;
import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.DslDraftHandler;
import cbs.nova.starter.controller.DslReloadHandler;
import cbs.nova.starter.exception.BuilderApiException;
import cbs.nova.starter.model.DslFileModels.FileContentResponse;
import cbs.nova.starter.model.DslFileModels.FlushResult;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionStatus;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.model.VcsModels.CommitRequest;
import cbs.nova.starter.model.VcsModels.CommitResult;
import cbs.nova.starter.model.VcsModels.DefinitionBundle;
import cbs.nova.starter.model.VcsModels.DefinitionBundleEntry;
import cbs.nova.starter.model.VcsModels.DiscardRequest;
import cbs.nova.starter.model.VcsModels.DiscardResult;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import cbs.nova.starter.model.VcsModels.DraftResponse;
import cbs.nova.starter.model.VcsModels.DraftSummary;
import cbs.nova.starter.model.VcsModels.ImportBundleResult;
import cbs.nova.starter.model.VcsModels.ImportEntryResult;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.EntityResponse;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

class DslDraftResourceTest {

  private Path sourceDir;
  private DslProperties props;
  private DslDraftHandler handler;
  private DslBuilderClient client;
  private DslSourcePathResolver sourcePathResolver;
  private DslGitStatusResolver gitStatusResolver;
  private DslDefinitionStatusResolver statusResolver;
  private DslDefinitionBundleService bundleService;
  private final ObjectMapper mapper = new ObjectMapper();
  private GlobalManager previousGlobalManager;

  @BeforeEach
  void setUp() throws IOException {
    previousGlobalManager = GlobalManager.globalManager();
    GlobalManager.globalManager().resetForTests();
    sourceDir = Files.createTempDirectory("dsl-draft-test-");
    props = DslProperties.builder().sourceDir(sourceDir.toString()).build();

    client = mock(DslBuilderClient.class);
    BuilderClientTestSupport.stubSuccessfulCompile(client);
    sourcePathResolver = mock(DslSourcePathResolver.class);
    gitStatusResolver = mock(DslGitStatusResolver.class);
    statusResolver = new DslDefinitionStatusResolver(props, gitStatusResolver, sourcePathResolver);
    bundleService = new DslDefinitionBundleService(mapper, Optional.empty(), props,
            sourcePathResolver, providerOf(client));

    handler = handler(client);
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
  void saveWritesSourceFileViaBuilder() throws Exception {
    Files.createDirectories(sourceDir.resolve("dsl"));
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));

    ServerResponse response = handler.save(postRequestWithSource(
            "/api/dsl/drafts/foo/save", "foo", "class Foo {}"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DraftResponse body = (DraftResponse) ((EntityResponse<?>) response).entity();
    assertThat(body.status()).isEqualTo("Draft");
    assertThat(body.location()).isEqualTo("dsl/FooDsl.java");
    assertThat(body.savedAt()).isNotNull();
    verify(client).stageWrite("dsl/FooDsl.java", "class Foo {}");
    verify(client).flushFiles();
    assertNoWorkbenchDir();
  }

  @Test
  void saveReturns400WhenSourceBlank() throws Exception {
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));

    ServerResponse response = handler.save(postRequestWithSource(
            "/api/dsl/drafts/foo/save", "foo", ""));

    assertThat(response.statusCode().value()).isEqualTo(400);
  }

  @Test
  void saveReturns404WhenNoSourcePath() throws Exception {
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.empty());

    ServerResponse response = handler.save(postRequestWithSource(
            "/api/dsl/drafts/foo/save", "foo", "class Foo {}"));

    assertThat(response.statusCode().value()).isEqualTo(404);
  }

  @Test
  void readReturnsSourceAndStatus() throws Exception {
    Files.createDirectories(sourceDir.resolve("dsl"));
    Files.writeString(sourceDir.resolve("dsl/FooDsl.java"), "class Foo {}");
    registerProcess("foo");
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));
    when(gitStatusResolver.status(sourceDir)).thenReturn(Optional.of(
            RepoStatus.of(sourceDir, Map.of("dsl/FooDsl.java", ChangeType.MODIFIED))));
    when(client.readFile("dsl/FooDsl.java")).thenReturn(
            new FileContentResponse("dsl/FooDsl.java", "class Foo {}", false, 0L));

    ServerResponse response = handler.read(getRequest("/api/dsl/drafts/foo",
            Map.of("name", "foo")));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DraftRequest body = (DraftRequest) ((EntityResponse<?>) response).entity();
    assertThat(body.source()).isEqualTo("class Foo {}");
    assertThat(body.status()).isEqualTo("Modified");
    assertThat(body.type()).isEqualTo("process");
    assertThat(body.savedAt()).isNull();
  }

  @Test
  void readReturns404WhenNoSourcePath() throws Exception {
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.empty());

    ServerResponse response = handler.read(getRequest("/api/dsl/drafts/foo",
            Map.of("name", "foo")));

    assertThat(response.statusCode().value()).isEqualTo(404);
  }

  @Test
  void listReturnsOnlyChangedDefinitions() throws Exception {
    registerProcess("foo");
    registerFunction("bar");
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));
    when(sourcePathResolver.relativePath("bar")).thenReturn(Optional.of("dsl/BarDsl.java"));
    when(gitStatusResolver.status(sourceDir)).thenReturn(Optional.of(
            RepoStatus.of(sourceDir, Map.of("dsl/FooDsl.java", ChangeType.MODIFIED))));

    ServerResponse response = handler.list(getRequest("/api/dsl/drafts", null));

    assertThat(response.statusCode().value()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    PageResponse<DraftSummary> body = (PageResponse<DraftSummary>) ((EntityResponse<?>) response)
            .entity();
    assertThat(body.items()).hasSize(1);
    assertThat(body.items().get(0).name()).isEqualTo("foo");
    assertThat(body.items().get(0).status()).isEqualTo("Modified");
  }

  @Test
  void deleteRevertsSourceInBuilderMode() throws Exception {
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));
    when(client.discard(any())).thenReturn(new DiscardResult(List.of("dsl/FooDsl.java")));

    ServerResponse response = handler.delete(deleteRequest("foo", "/api/dsl/drafts/foo"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DraftResponse body = (DraftResponse) ((EntityResponse<?>) response).entity();
    assertThat(body.status()).isEqualTo("Discarded");
    verify(client).discard(new DiscardRequest(List.of("dsl/FooDsl.java")));
  }

  @Test
  void historyIsGitOnlyInBuilderMode() throws Exception {
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));
    when(client.vcsLog("dsl/FooDsl.java", 50)).thenReturn(List.of(
            new cbs.nova.starter.model.VcsModels.LogEntry("abc123", 1L, "alice", "x")));

    ServerResponse response = handler.history(getRequest("/api/dsl/drafts/foo/history",
            Map.of("name", "foo")));

    assertThat(response.statusCode().value()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    List<cbs.nova.starter.model.VcsModels.DefinitionHistoryEntry> body = (List<cbs.nova.starter.model.VcsModels.DefinitionHistoryEntry>) ((EntityResponse<?>) response)
            .entity();
    assertThat(body).hasSize(1);
    assertThat(body.get(0).timestamp()).isEqualTo("abc123");
  }

  @Test
  void publishWritesSourceThenCommits() throws Exception {
    Files.createDirectories(sourceDir.resolve("dsl"));
    registerProcess("foo");
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));
    when(gitStatusResolver.status(sourceDir)).thenReturn(Optional.of(
            RepoStatus.of(sourceDir, Map.of("dsl/FooDsl.java", ChangeType.MODIFIED))));
    when(client.commit(any())).thenReturn(
            new CommitResult("abc123", List.of("dsl/FooDsl.java"), 1L, null, null));

    ServerResponse response = handler.publish(postRequestWithSource(
            "/api/dsl/drafts/foo/publish", "foo", "class Foo {}"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DraftResponse body = (DraftResponse) ((EntityResponse<?>) response).entity();
    assertThat(body.reloaded()).isTrue();
    assertThat(body.commitId()).isEqualTo("abc123");
    verify(client).stageWrite("dsl/FooDsl.java", "class Foo {}");
    verify(client).commit(any(CommitRequest.class));
  }

  @Test
  void publishCommitsExistingChangeWhenNoSourceGiven() throws Exception {
    Files.createDirectories(sourceDir.resolve("dsl"));
    Files.writeString(sourceDir.resolve("dsl/FooDsl.java"), "class Foo {}");
    registerProcess("foo");
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));
    when(gitStatusResolver.status(sourceDir)).thenReturn(Optional.of(
            RepoStatus.of(sourceDir, Map.of("dsl/FooDsl.java", ChangeType.MODIFIED))));
    when(client.commit(any())).thenReturn(
            new CommitResult("abc123", List.of("dsl/FooDsl.java"), 1L, null, null));

    ServerResponse response = handler.publish(postRequestWithSource(
            "/api/dsl/drafts/foo/publish", "foo", ""));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DraftResponse body = (DraftResponse) ((EntityResponse<?>) response).entity();
    assertThat(body.commitId()).isEqualTo("abc123");
    verify(client, never()).stageWrite(eq("dsl/FooDsl.java"), anyString());
  }

  @Test
  void publishDoesNotCommitWhenReloadFails() throws Exception {
    Files.createDirectories(sourceDir.resolve("dsl"));
    Files.writeString(sourceDir.resolve("dsl/FooDsl.java"), "broken");
    registerProcess("foo");
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));
    when(client.compile(any(cbs.nova.starter.model.CompileModels.CompileRequest.class))).thenReturn(
            new cbs.nova.starter.model.CompileModels.CompileResult("s-1", false,
                    List.of(), List.of("bad"), 1));

    ServerResponse response = handler.publish(postRequestWithSource(
            "/api/dsl/drafts/foo/publish", "foo", "broken"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DraftResponse body = (DraftResponse) ((EntityResponse<?>) response).entity();
    assertThat(body.reloaded()).isFalse();
    assertThat(body.commitId()).isNull();
  }

  @Test
  void importBundleWritesFilesAndCommits() throws Exception {
    Files.createDirectories(sourceDir.resolve("dsl"));
    registerProcess("A");
    when(sourcePathResolver.relativePath("A")).thenReturn(Optional.of("dsl/ADsl.java"));
    when(client.commit(any())).thenReturn(
            new CommitResult("import123", List.of("dsl/ADsl.java"), 1L, null, null));

    DefinitionBundle bundle = new DefinitionBundle(1, "dev", "now",
            List.of(new DefinitionBundleEntry(
                    new DraftRequest("A", "process", null, null, null, "class A {}", null),
                    "source")),
            null);

    ServerResponse response = handler.importBundle(postBundle(bundle));

    assertThat(response.statusCode().value()).isEqualTo(200);
    ImportBundleResult body = (ImportBundleResult) ((EntityResponse<?>) response).entity();
    assertThat(body.dryRun()).isFalse();
    assertThat(body.reloaded()).isTrue();
    assertThat(Files.readString(sourceDir.resolve("dsl/ADsl.java"))).isEqualTo("class A {}");
    verify(client).commit(any(CommitRequest.class));
  }

  @Test
  void importBundleDryRunReportsDiff() throws Exception {
    Files.createDirectories(sourceDir.resolve("dsl"));
    Files.writeString(sourceDir.resolve("dsl/ADsl.java"), "class A { old }");
    registerProcess("A");
    when(sourcePathResolver.relativePath("A")).thenReturn(Optional.of("dsl/ADsl.java"));

    DefinitionBundle bundle = new DefinitionBundle(1, "dev", "now",
            List.of(new DefinitionBundleEntry(
                    new DraftRequest("A", "process", null, null, null, "class A {}", null),
                    "source")),
            null);

    ServerResponse response = handler.importBundle(postBundle(bundle, true));

    assertThat(response.statusCode().value()).isEqualTo(200);
    ImportBundleResult body = (ImportBundleResult) ((EntityResponse<?>) response).entity();
    assertThat(body.dryRun()).isTrue();
    ImportEntryResult entry = body.results().get(0);
    assertThat(entry.outcome()).isEqualTo("updated");
    assertThat(Files.readString(sourceDir.resolve("dsl/ADsl.java"))).isEqualTo("class A { old }");
  }

  @Test
  void importBundleSkipsUnknownNames() throws Exception {
    Files.createDirectories(sourceDir.resolve("dsl"));
    when(sourcePathResolver.relativePath("Unknown")).thenReturn(Optional.empty());

    DefinitionBundle bundle = new DefinitionBundle(1, "dev", "now",
            List.of(new DefinitionBundleEntry(
                    new DraftRequest("Unknown", "process", null, null, null, "class U {}", null),
                    "source")),
            null);

    ServerResponse response = handler.importBundle(postBundle(bundle));

    assertThat(response.statusCode().value()).isEqualTo(200);
    ImportBundleResult body = (ImportBundleResult) ((EntityResponse<?>) response).entity();
    ImportEntryResult entry = body.results().get(0);
    assertThat(entry.outcome()).isEqualTo("skipped");
  }

  @Test
  void noWorkbenchDirCreated() throws Exception {
    Files.createDirectories(sourceDir.resolve("dsl"));
    registerProcess("foo");
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));
    when(gitStatusResolver.status(sourceDir)).thenReturn(Optional.of(
            RepoStatus.of(sourceDir, Map.of())));
    when(client.readFile("dsl/FooDsl.java")).thenReturn(
            new FileContentResponse("dsl/FooDsl.java", "class Foo {}", false, 0L));

    handler.save(postRequestWithSource("/api/dsl/drafts/foo/save", "foo", "class Foo {}"));
    handler.read(getRequest("/api/dsl/drafts/foo", Map.of("name", "foo")));
    handler.list(getRequest("/api/dsl/drafts", null));

    assertNoWorkbenchDir();
  }

  private void assertNoWorkbenchDir() throws IOException {
    try (var stream = Files.list(sourceDir)) {
      assertThat(stream.toList()).containsExactly(sourceDir.resolve("dsl"));
    }
  }

  private DslDraftHandler handler(DslBuilderClient client) {
    return new DslDraftHandler(props,
            new DslReloadHandler(props, new DefinitionLoader(), null, null,
                    providerOf(client), null, null, null),
            mapper, bundleService,
            null, providerOf(client), null, null, null,
            providerOfBean(sourcePathResolver), providerOfBean(gitStatusResolver),
            providerOfBean(statusResolver));
  }

  private void registerProcess(String name) {
    GlobalManager.globalManager().registerProcess(
            Dsl.process(name).execute(ctx -> cbs.nova.dsl.Result.success("ok")).build());
  }

  private void registerFunction(String name) {
    GlobalManager.globalManager().registerFunction(
            Dsl.function(name).execute(ctx -> cbs.nova.dsl.Result.success("ok")).build());
  }

  private static ServerRequest postRequestWithSource(String path, String name, String source) {
    var req = new MockHttpServletRequest("POST", path);
    req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("name", name));
    req.setContentType("application/json");
    String body = "{\"name\":\"" + name + "\",\"source\":\"" + source + "\"}";
    req.setContent(body.getBytes(StandardCharsets.UTF_8));
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

  private static ServerRequest postBundle(String path, String body) {
    int q = path.indexOf('?');
    String requestPath = q < 0 ? path : path.substring(0, q);
    var req = new MockHttpServletRequest("POST", requestPath);
    if (q >= 0) {
      req.setQueryString(path.substring(q + 1));
      for (String pair : path.substring(q + 1).split("&")) {
        String[] kv = pair.split("=", 2);
        if (kv.length == 2) {
          req.addParameter(kv[0], kv[1]);
        }
      }
    }
    req.setContentType("application/json");
    req.setContent(body.getBytes(StandardCharsets.UTF_8));
    return ServerRequest.create(req, CONVERTERS);
  }

  private ServerRequest postBundle(DefinitionBundle bundle) throws IOException {
    return postBundle("/api/dsl/definitions/import", mapper.writeValueAsString(bundle));
  }

  private ServerRequest postBundle(DefinitionBundle bundle, boolean dryRun) throws IOException {
    return postBundle("/api/dsl/definitions/import?dryRun=true", mapper.writeValueAsString(bundle));
  }

  private static final List<org.springframework.http.converter.HttpMessageConverter<?>> CONVERTERS = List
          .of(new org.springframework.http.converter.ByteArrayHttpMessageConverter(),
                  new org.springframework.http.converter.StringHttpMessageConverter());

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> providerOfBean(T bean) {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(bean);
    return provider;
  }
}
