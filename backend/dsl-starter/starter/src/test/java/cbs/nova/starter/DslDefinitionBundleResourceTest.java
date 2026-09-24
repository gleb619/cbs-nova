package cbs.nova.starter;

import static cbs.nova.starter.BuilderClientTestSupport.emptyZip;
import static cbs.nova.starter.BuilderClientTestSupport.providerOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.utils.DefinitionLoader;
import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.DslDraftHandler;
import cbs.nova.starter.controller.DslReloadHandler;
import cbs.nova.starter.model.VcsModels.CommitResult;
import cbs.nova.starter.model.VcsModels.DefinitionBundle;
import cbs.nova.starter.model.VcsModels.DefinitionBundleEntry;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import cbs.nova.starter.model.VcsModels.ImportBundleResult;
import cbs.nova.starter.model.DslFileModels.FileContentResponse;
import cbs.nova.starter.service.DslDefinitionBundleService;
import cbs.nova.starter.service.DslDefinitionStatusResolver;
import cbs.nova.starter.service.DslGitStatusResolver;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.EntityResponse;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

class DslDefinitionBundleResourceTest {

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
    sourceDir = Files.createTempDirectory("dsl-bundle-test-");
    props = DslProperties.builder().sourceDir(sourceDir.toString()).build();
    client = mock(DslBuilderClient.class);
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
  void exportReadsHeadByDefault() throws Exception {
    Files.writeString(sourceDir.resolve("A.java"), "class A { head }");
    GlobalManager.globalManager().registerProcess(
            cbs.nova.dsl.Dsl.process("A").execute(ctx -> cbs.nova.dsl.Result.success("ok"))
                    .build());
    when(sourcePathResolver.relativePath("A")).thenReturn(Optional.of("A.java"));
    when(client.vcsShow("A.java", "HEAD")).thenReturn("class A { head }");

    ServerResponse response = handler.exportBundle(getRequest(
            "/api/dsl/definitions/export", null));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DefinitionBundle body = (DefinitionBundle) ((EntityResponse<?>) response).entity();
    assertThat(body.definitions()).hasSize(1);
    assertThat(body.definitions().get(0).definition().source())
            .isEqualTo("class A { head }");
    assertThat(body.definitions().get(0).source()).isEqualTo("source");
  }

  @Test
  void exportReadsWorkingTreeWithIncludeDrafts() throws Exception {
    Files.writeString(sourceDir.resolve("A.java"), "class A { draft }");
    GlobalManager.globalManager().registerProcess(
            cbs.nova.dsl.Dsl.process("A").execute(ctx -> cbs.nova.dsl.Result.success("ok"))
                    .build());
    when(sourcePathResolver.relativePath("A")).thenReturn(Optional.of("A.java"));
    when(client.readFile("A.java")).thenReturn(
            new FileContentResponse("A.java", "class A { draft }", false, 0L));

    ServerResponse response = handler.exportBundle(getRequest(
            "/api/dsl/definitions/export?include=drafts", null));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DefinitionBundle body = (DefinitionBundle) ((EntityResponse<?>) response).entity();
    assertThat(body.definitions().get(0).definition().source())
            .isEqualTo("class A { draft }");
    assertThat(body.definitions().get(0).source()).isEqualTo("draft");
  }

  @Test
  void importBundleWritesSourceFiles() throws Exception {
    Files.createDirectories(sourceDir.resolve("dsl"));
    when(sourcePathResolver.relativePath("A")).thenReturn(Optional.of("dsl/ADsl.java"));
    when(client.commit(any())).thenReturn(
            new CommitResult("import123", List.of("dsl/ADsl.java"), 1L, null, null));
    when(client.compile(any())).thenReturn(
            new cbs.nova.starter.model.CompileModels.CompileResult("s-1", true,
                    List.of(), List.of(), 1));
    when(client.downloadZip(anyString())).thenReturn(emptyZip());

    DefinitionBundle bundle = bundle("class A {}");
    ServerResponse response = handler.importBundle(postBundle(bundle));

    assertThat(response.statusCode().value()).isEqualTo(200);
    ImportBundleResult body = (ImportBundleResult) ((EntityResponse<?>) response).entity();
    assertThat(body.reloaded()).isTrue();
    assertThat(Files.readString(sourceDir.resolve("dsl/ADsl.java"))).isEqualTo("class A {}");
  }

  @Test
  void importBundleCommitsAllWrittenPathsInOneCommit() throws Exception {
    Files.createDirectories(sourceDir.resolve("dsl"));
    when(sourcePathResolver.relativePath("A")).thenReturn(Optional.of("dsl/ADsl.java"));
    when(sourcePathResolver.relativePath("B")).thenReturn(Optional.of("dsl/BDsl.java"));
    when(client.commit(any())).thenReturn(
            new CommitResult("import123", List.of("dsl/ADsl.java", "dsl/BDsl.java"), 1L, null,
                    null));
    when(client.compile(any())).thenReturn(
            new cbs.nova.starter.model.CompileModels.CompileResult("s-1", true,
                    List.of(), List.of(), 1));
    when(client.downloadZip(anyString())).thenReturn(emptyZip());

    DefinitionBundle bundle = new DefinitionBundle(1, "dev", "now", List.of(
            new DefinitionBundleEntry(
                    new DraftRequest("A", "process", null, null, null, "class A {}", null),
                    "source"),
            new DefinitionBundleEntry(
                    new DraftRequest("B", "process", null, null, null, "class B {}", null),
                    "source")),
            null);

    handler.importBundle(postBundle(bundle));

    verify(client).commit(org.mockito.ArgumentMatchers.argThat(
            r -> r.paths().containsAll(List.of("dsl/ADsl.java", "dsl/BDsl.java"))
                    && r.message().equals("Import bundle")));
  }

  @Test
  void importBundleDryRunDoesNotWriteFiles() throws Exception {
    Files.createDirectories(sourceDir.resolve("dsl"));
    Files.writeString(sourceDir.resolve("dsl/ADsl.java"), "class A { old }");
    when(sourcePathResolver.relativePath("A")).thenReturn(Optional.of("dsl/ADsl.java"));

    DefinitionBundle bundle = bundle("class A {}");
    ServerResponse response = handler.importBundle(postBundle(bundle, true));

    assertThat(response.statusCode().value()).isEqualTo(200);
    ImportBundleResult body = (ImportBundleResult) ((EntityResponse<?>) response).entity();
    assertThat(body.dryRun()).isTrue();
    assertThat(Files.readString(sourceDir.resolve("dsl/ADsl.java"))).isEqualTo("class A { old }");
  }

  @Test
  void importBundleSkipsUnknownNames() throws Exception {
    Files.createDirectories(sourceDir.resolve("dsl"));
    when(sourcePathResolver.relativePath("Unknown")).thenReturn(Optional.empty());

    DefinitionBundle bundle = new DefinitionBundle(1, "dev", "now", List.of(
            new DefinitionBundleEntry(
                    new DraftRequest("Unknown", "process", null, null, null, "class U {}", null),
                    "source")),
            null);

    ServerResponse response = handler.importBundle(postBundle(bundle));

    ImportBundleResult body = (ImportBundleResult) ((EntityResponse<?>) response).entity();
    assertThat(body.results().get(0).outcome()).isEqualTo("skipped");
  }

  @Test
  void noWorkbenchDirCreatedDuringBundleOperations() throws Exception {
    Files.writeString(sourceDir.resolve("A.java"), "class A {}");
    GlobalManager.globalManager().registerProcess(
            cbs.nova.dsl.Dsl.process("A").execute(ctx -> cbs.nova.dsl.Result.success("ok"))
                    .build());
    when(sourcePathResolver.relativePath("A")).thenReturn(Optional.of("A.java"));
    when(client.vcsShow("A.java", "HEAD")).thenReturn("class A {}");

    handler.exportBundle(getRequest("/api/dsl/definitions/export", null));

    try (var stream = Files.list(sourceDir)) {
      assertThat(stream.toList()).containsExactly(sourceDir.resolve("A.java"));
    }
  }

  private DefinitionBundle bundle(String source) {
    return new DefinitionBundle(1, "dev", "now", List.of(
            new DefinitionBundleEntry(
                    new DraftRequest("A", "process", null, null, null, source, null),
                    "source")),
            null);
  }

  private DslDraftHandler handler(DslBuilderClient client) {
    return new DslDraftHandler(props,
            new DslReloadHandler(props, new DefinitionLoader(), null, null, providerOf(client),
                    null, null, null),
            mapper, bundleService,
            null, providerOf(client), null, null, null,
            providerOfBean(sourcePathResolver), providerOfBean(gitStatusResolver),
            providerOfBean(statusResolver));
  }

  private static ServerRequest getRequest(String path, Map<String, String> pathVariables) {
    int q = path.indexOf('?');
    String requestPath = q < 0 ? path : path.substring(0, q);
    var req = new MockHttpServletRequest("GET", requestPath);
    if (q >= 0) {
      req.setQueryString(path.substring(q + 1));
      for (String pair : path.substring(q + 1).split("&")) {
        String[] kv = pair.split("=", 2);
        if (kv.length == 2) {
          req.addParameter(kv[0], kv[1]);
        }
      }
    }
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
