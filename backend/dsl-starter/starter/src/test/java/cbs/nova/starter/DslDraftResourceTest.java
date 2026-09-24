package cbs.nova.starter;

import static java.nio.charset.StandardCharsets.*;
import static cbs.nova.starter.BuilderClientTestSupport.providerOf;
import static cbs.nova.starter.BuilderClientTestSupport.stubLocalCompile;
import static cbs.nova.starter.BuilderClientTestSupport.stubSuccessfulCompile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.utils.DefinitionLoader;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.model.LoadResult;
import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.router.DslDraftRouterConfiguration;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.DslDraftHandler;
import cbs.nova.starter.controller.DslReloadHandler;
import cbs.nova.starter.exception.BuilderApiException;
import cbs.nova.starter.exception.DslCompilationException;
import cbs.nova.dsl.model.CompileDiagnostic;
import cbs.nova.starter.model.VcsModels.CommitRequest;
import cbs.nova.starter.model.VcsModels.CommitResult;
import cbs.nova.starter.model.VcsModels.DefinitionBundle;
import cbs.nova.starter.model.VcsModels.DefinitionHistoryEntry;
import cbs.nova.starter.model.VcsModels.DiscardRequest;
import cbs.nova.starter.model.VcsModels.DiscardResult;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import cbs.nova.starter.model.VcsModels.HistoryDiffResponse;
import cbs.nova.starter.model.VcsModels.DraftResponse;
import cbs.nova.starter.model.VcsModels.DraftSummary;
import cbs.nova.starter.model.VcsModels.ImportBundleResult;
import cbs.nova.starter.model.VcsModels.LogEntry;
import cbs.nova.starter.model.DslFileModels.BulkWriteRequest;
import cbs.nova.starter.model.DslFileModels.BulkWriteResult;
import cbs.nova.starter.model.DslFileModels.FileContentRequest;
import cbs.nova.starter.model.DslFileModels.FileContentResponse;
import cbs.nova.starter.model.DslFileModels.FileEntry;
import cbs.nova.starter.model.DslFileModels.FlushResult;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.service.DslDefinitionHistoryService;
import cbs.nova.starter.service.DslDefinitionBundleService;
import cbs.nova.starter.service.DslGitStatusResolver;
import cbs.nova.starter.service.DslSourcePathResolver;
import cbs.nova.dsl.vcs.RepoStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.EntityResponse;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
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
  private DslBuilderClient client;
  private DslSourcePathResolver sourcePathResolver;
  private DslGitStatusResolver gitStatusResolver;

  @BeforeEach
  void setUp() throws IOException {
    sourceDir = Files.createTempDirectory("dsl-draft-test-");
    props = DslProperties.builder().sourceDir(sourceDir.toString()).build();

    client = mock(DslBuilderClient.class);
    sourcePathResolver = mock(DslSourcePathResolver.class);
    gitStatusResolver = mock(DslGitStatusResolver.class);

    stubLocalCompile(client);
    stubAllClientMethods();

    DslReloadHandler reloadHandler = new DslReloadHandler(props,
            new DefinitionLoader(), null, null, providerOf(client), null, null, null);

    handler = new DslDraftHandler(props,
            reloadHandler,
            new DslDefinitionHistoryService(props, mapper), mapper,
            new DslDefinitionBundleService(mapper, Optional.empty(),
                    DslProperties.bundleServiceDefaults()),
            null, providerOf(client), null, null, null,
            providerOfBean(sourcePathResolver), providerOfBean(gitStatusResolver));
  }

  private void stubAllClientMethods() {
    String location = "/remote/.workbench/drafts/X.json";
    lenient().when(client.saveDraft(anyString(), any(DraftRequest.class)))
            .thenAnswer(inv -> {
              String name = inv.getArgument(0);
              return new DraftResponse(name, "Draft",
                      "/remote/.workbench/drafts/" + name + ".json", false,
                      LoadResult.empty(), null, null, System.currentTimeMillis(), null);
            });
    lenient().when(client.publishDraft(anyString(), any(DraftRequest.class)))
            .thenAnswer(inv -> {
              String name = inv.getArgument(0);
              return new DraftResponse(name, "Published",
                      "/remote/.workbench/published/" + name + ".json", false,
                      LoadResult.empty(), null, null, System.currentTimeMillis(), null);
            });
    lenient().when(client.readDraft(anyString()))
            .thenAnswer(inv -> {
              String name = inv.getArgument(0);
              return new DraftRequest(name, "process", "Draft", "1", null, null,
                      System.currentTimeMillis());
            });
    lenient().when(client.deleteDraft(anyString()))
            .thenAnswer(inv -> {
              String name = inv.getArgument(0);
              return new DraftResponse(name, "Deleted", null, false, LoadResult.empty(),
                      null, null, System.currentTimeMillis(), null);
            });
    lenient().when(client.listDrafts(anyInt(), anyInt()))
            .thenReturn(new PageResponse<>(List.of(), 0L, 0, 50));
    lenient().when(client.history(anyString())).thenReturn(List.of());
    lenient().when(client.historyEntry(anyString(), anyString()))
            .thenThrow(new BuilderApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "not found"));
    lenient().when(client.historyDiff(anyString(), anyString()))
            .thenThrow(new BuilderApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "not found"));
    lenient().when(client.restoreDraft(anyString(), anyString()))
            .thenThrow(new BuilderApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "not found"));
    lenient().when(client.exportBundle(any(Boolean.class)))
            .thenReturn(new DefinitionBundle(1, "1", "2025-01-01T00:00:00Z", List.of(), null));
    lenient().when(client.importBundle(any(DefinitionBundle.class), any(Boolean.class)))
            .thenReturn(new ImportBundleResult(false, true, 0, 0, List.of(), null, List.of()));
    lenient().when(client.discard(any(DiscardRequest.class)))
            .thenReturn(new DiscardResult(List.of()));
    lenient().when(client.commit(any(CommitRequest.class)))
            .thenReturn(new CommitResult("abc", List.of(), 0L, false, null));
    lenient().when(client.vcsLog(anyString(), anyInt())).thenReturn(List.of());
    lenient().when(client.vcsShow(anyString(), anyString()))
            .thenThrow(new BuilderApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "not found"));
    lenient().when(client.readFile(anyString()))
            .thenThrow(new BuilderApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "not found"));
    lenient().when(client.fileExists(anyString())).thenReturn(false);
    lenient().when(client.listFiles(anyString())).thenReturn(List.of());
    lenient().when(client.pendingCount()).thenReturn(0);
    lenient().when(client.vcsStatus()).thenReturn(Optional.empty());
    lenient().doNothing().when(client).stageWrite(anyString(), anyString());
    lenient().when(client.stageAll(any()))
            .thenReturn(new BulkWriteResult(0, 0, List.of()));
    lenient().when(client.flushFiles())
            .thenReturn(new FlushResult(0, 0, List.of()));
  }

  private static <T> ObjectProvider<T> providerOfBean(T bean) {
    @SuppressWarnings("unchecked")
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(bean);
    return provider;
  }

  @AfterEach
  void tearDown() throws IOException {
    if (sourceDir != null && Files.exists(sourceDir)) {
      deleteRecursively(sourceDir);
    }
  }

  static final List<HttpMessageConverter<?>> CONVERTERS = List
          .of(new InputStreamHttpMessageConverter(), new StringBodyHttpMessageConverter());

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

  private static ServerRequest postRequestWithSource(String path, String name, String version,
          String source) {
    var req = new MockHttpServletRequest("POST", path);
    req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("name", name));
    req.setContentType("application/json");
    req.setContent(
            ("{\"name\":\"" + name
                    + "\",\"type\":\"process\",\"status\":\"Draft\",\"version\":\"" + version
                    + "\",\"source\":\"" + source + "\"}")
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
    verify(client).saveDraft(eq("foo"), any(DraftRequest.class));
  }

  @Test
  void publishPersistsPublishedJson() throws Exception {
    ServerResponse response = handler.publish(postRequest("/api/dsl/drafts/foo/publish"));
    assertThat(response.statusCode().value()).isEqualTo(200);
    verify(client).publishDraft(eq("foo"), any(DraftRequest.class));
  }

  @Test
  void deleteRemovesDraftJson() throws Exception {
    ServerResponse response = handler.delete(deleteRequest("foo", "/api/dsl/drafts/foo"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    verify(client).deleteDraft("foo");
  }

  @Test
  void deleteReturns404WhenDraftUnknown() throws Exception {
    when(client.deleteDraft("foo"))
            .thenThrow(new BuilderApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "not found"));
    ServerResponse response = handler.delete(deleteRequest("foo", "/api/dsl/drafts/foo"));

    assertThat(response.statusCode().value()).isEqualTo(404);
  }

  @Test
  void deleteReturns409WhenSourceDirBlank() throws Exception {
    DslProperties blank = DslProperties.builder().sourceDir("").build();
    DslBuilderClient blankClient = mock(DslBuilderClient.class);
    stubLocalCompile(blankClient);
    handler = new DslDraftHandler(
            blank,
            new DslReloadHandler(blank, new DefinitionLoader(), null, null,
                    providerOf(blankClient), null, null, null),
            new DslDefinitionHistoryService(blank, mapper),
            mapper,
            new DslDefinitionBundleService(mapper, Optional.empty(),
                    DslProperties.bundleServiceDefaults()),
            null, providerOf(blankClient), null, null, null,
            providerOfBean(sourcePathResolver), providerOfBean(gitStatusResolver));
    ServerResponse response = handler.delete(deleteRequest("foo", "/api/dsl/drafts/foo"));
    assertThat(response.statusCode().value()).isEqualTo(409);
  }

  @Test
  void deleteLeavesPublishedJsonUntouched() throws Exception {
    ServerResponse response = handler.delete(deleteRequest("foo", "/api/dsl/drafts/foo"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    verify(client).deleteDraft("foo");
  }

  @Test
  void deleteRejectsPathTraversal() throws Exception {
    Path outside = sourceDir.resolveSibling("escapee.json");
    Files.writeString(outside, "{}", UTF_8);

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
    DslProperties blank = DslProperties.builder().sourceDir("").build();
    DslBuilderClient blankClient = mock(DslBuilderClient.class);
    stubLocalCompile(blankClient);
    handler = new DslDraftHandler(
            blank,
            new DslReloadHandler(blank, new DefinitionLoader(), null, null,
                    providerOf(blankClient), null, null, null),
            new DslDefinitionHistoryService(blank, mapper),
            mapper,
            new DslDefinitionBundleService(mapper, Optional.empty(),
                    DslProperties.bundleServiceDefaults()),
            null, providerOf(blankClient), null, null, null,
            providerOfBean(sourcePathResolver), providerOfBean(gitStatusResolver));
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
  void listReturnsEmptyEnvelopeWhenDraftsDirMissing() throws Exception {
    ServerResponse response = handler.list(getRequest("/api/dsl/drafts", null));

    assertThat(response.statusCode().value()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    PageResponse<DraftSummary> body = (PageResponse<DraftSummary>) ((EntityResponse<?>) response)
            .entity();
    assertThat(body.items()).isEmpty();
    assertThat(body.total()).isEqualTo(0L);
    assertThat(body.offset()).isEqualTo(0);
    assertThat(body.limit()).isEqualTo(50);
  }

  @Test
  void listReturnsEmptyEnvelopeWhenSourceDirBlank() throws Exception {
    DslProperties blank = DslProperties.builder().sourceDir("").build();
    DslBuilderClient blankClient = mock(DslBuilderClient.class);
    stubLocalCompile(blankClient);
    when(blankClient.listDrafts(anyInt(), anyInt()))
            .thenReturn(new PageResponse<>(List.of(), 0L, 0, 50));
    handler = new DslDraftHandler(
            blank,
            new DslReloadHandler(blank, new DefinitionLoader(), null, null,
                    providerOf(blankClient), null, null, null),
            new DslDefinitionHistoryService(blank, mapper),
            mapper,
            new DslDefinitionBundleService(mapper, Optional.empty(),
                    DslProperties.bundleServiceDefaults()),
            null, providerOf(blankClient), null, null, null,
            providerOfBean(sourcePathResolver), providerOfBean(gitStatusResolver));

    ServerResponse response = handler.list(getRequest("/api/dsl/drafts", null));

    assertThat(response.statusCode().value()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    PageResponse<DraftSummary> body = (PageResponse<DraftSummary>) ((EntityResponse<?>) response)
            .entity();
    assertThat(body.items()).isEmpty();
    assertThat(body.total()).isEqualTo(0L);
  }

  @Test
  void listReturnsPaginatedSummariesForSavedDrafts() throws Exception {
    when(client.listDrafts(50, 0)).thenReturn(
            new PageResponse<>(List.of(
                    new DraftSummary("foo", "process", "Draft", "1", 1000L),
                    new DraftSummary("bar", "process", "Published", "1", 2000L)),
                    2L, 0, 50));

    ServerResponse response = handler.list(getRequest("/api/dsl/drafts", null));

    assertThat(response.statusCode().value()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    PageResponse<DraftSummary> body = (PageResponse<DraftSummary>) ((EntityResponse<?>) response)
            .entity();
    assertThat(body.items()).hasSize(2);
    assertThat(body.total()).isEqualTo(2L);
    assertThat(body.items())
            .extracting(DraftSummary::name)
            .containsExactlyInAnyOrder("foo", "bar");
    assertThat(body.items())
            .allSatisfy(s -> {
              assertThat(s.name()).isNotBlank();
              assertThat(s.updatedAt()).isGreaterThan(0L);
              assertThat(s.status()).isNotBlank();
            });
  }

  @Test
  void listSkipsUnparseableDraftFiles() throws Exception {
    when(client.listDrafts(50, 0)).thenReturn(
            new PageResponse<>(List.of(new DraftSummary("foo", "process", "Draft", "1", 1000L)),
                    1L, 0, 50));

    ServerResponse response = handler.list(getRequest("/api/dsl/drafts", null));

    assertThat(response.statusCode().value()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    PageResponse<DraftSummary> body = (PageResponse<DraftSummary>) ((EntityResponse<?>) response)
            .entity();
    assertThat(body.items()).hasSize(1);
    assertThat(body.total()).isEqualTo(1L);
    assertThat(body.items().get(0).name()).isEqualTo("foo");
  }

  @Test
  void readReturnsDraftPayload() throws Exception {
    when(client.readDraft("foo")).thenReturn(
            new DraftRequest("foo", "process", "Draft", "1", null, "workbench",
                    System.currentTimeMillis()));

    ServerResponse response = handler
            .read(getRequest("/api/dsl/drafts/foo", Map.of("name", "foo")));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DraftRequest body = (DraftRequest) ((EntityResponse<?>) response)
            .entity();
    assertThat(body.name()).isEqualTo("foo");
    assertThat(body.status()).isEqualTo("Draft");
    assertThat(body.source()).isEqualTo("workbench");
    assertThat(body.savedAt()).isNotNull();
  }

  @Test
  void readReturns404WhenUnknown() throws Exception {
    when(client.readDraft("missing"))
            .thenThrow(new BuilderApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "not found"));
    ServerResponse response = handler
            .read(getRequest("/api/dsl/drafts/missing", Map.of("name", "missing")));

    assertThat(response.statusCode().value()).isEqualTo(404);
  }

  @Test
  void readRejectsPathTraversal() throws Exception {
    Path outside = sourceDir.resolveSibling("escapee.json");
    Files.writeString(outside, "{\"name\":\"escapee\"}", UTF_8);

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
            .withPropertyValues("cbs.dsl.drafts.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(RouterFunction.class));
  }

  @Test
  void publishSurfacesCompileDiagnosticsWhenReloadFails() throws Exception {
    Files.writeString(sourceDir.resolve("Broken.java"),
            "this is not valid Java at all; { class Broken { ???");
    DslBuilderClient reloadClient = mock(DslBuilderClient.class);
    stubLocalCompile(reloadClient);
    lenient().when(reloadClient.publishDraft(anyString(), any(DraftRequest.class)))
            .thenAnswer(inv -> {
              String name = inv.getArgument(0);
              return new DraftResponse(name, "Published",
                      "/remote/.workbench/published/" + name + ".json", false,
                      LoadResult.empty(), null, null, null, null);
            });
    DslDraftHandler localHandler = new DslDraftHandler(props,
            new DslReloadHandler(props, new DefinitionLoader(), null, null,
                    providerOf(reloadClient), null, null, null),
            new DslDefinitionHistoryService(props, mapper), mapper,
            new DslDefinitionBundleService(mapper, Optional.empty(),
                    DslProperties.bundleServiceDefaults()),
            null, providerOf(reloadClient), null, null, null,
            providerOfBean(sourcePathResolver), providerOfBean(gitStatusResolver));

    ServerResponse response = localHandler.publish(postRequest("/api/dsl/drafts/foo/publish"));
    assertThat(response.statusCode().value()).isEqualTo(200);

    Object entity = ((EntityResponse<?>) response)
            .entity();
    assertThat(entity).isInstanceOf(DraftResponse.class);
    DraftResponse draft = (DraftResponse) entity;
    assertThat(draft.reloaded()).isFalse();
    assertThat(draft.reloadError()).isNotBlank();
    assertThat(draft.diagnostics()).isNotEmpty();
    assertThat(draft.diagnostics().get(0).message()).contains("Broken.java");
    assertThat(draft.diagnostics().get(0).severity()).isEqualTo("error");
  }

  @Test
  void publishSnapshotsPreviousPublishedPayload() throws Exception {
    when(client.publishDraft(eq("X"), any(DraftRequest.class)))
            .thenAnswer(inv -> new DraftResponse("X", "Published",
                    "/remote/.workbench/published/X.json", false, LoadResult.empty(),
                    null, null, null, null));

    handler.publish(postRequest("/api/dsl/drafts/X/publish", "X", "A"));
    Thread.sleep(2);
    handler.publish(postRequest("/api/dsl/drafts/X/publish", "X", "B"));

    verify(client).publishDraft(eq("X"), any(DraftRequest.class));
  }

  @Test
  void firstPublishCreatesNoHistoryEntry() throws Exception {
    handler.publish(postRequest("/api/dsl/drafts/X/publish", "X", "1"));

    verify(client).publishDraft(eq("X"), any(DraftRequest.class));
  }

  @Test
  void historyLimitPrunesOldSnapshots() throws Exception {
    props = DslProperties.builder().sourceDir(sourceDir.toString())
            .drafts(new DslProperties.Drafts(2))
            .build();
    DslBuilderClient limitClient = mock(DslBuilderClient.class);
    stubLocalCompile(limitClient);
    lenient().when(limitClient.publishDraft(anyString(), any(DraftRequest.class)))
            .thenAnswer(inv -> {
              String name = inv.getArgument(0);
              return new DraftResponse(name, "Published",
                      "/remote/.workbench/published/" + name + ".json", false,
                      LoadResult.empty(), null, null, null, null);
            });
    handler = new DslDraftHandler(props,
            new DslReloadHandler(props, new DefinitionLoader(), null, null,
                    providerOf(limitClient), null, null, null),
            new DslDefinitionHistoryService(props, mapper), mapper,
            new DslDefinitionBundleService(mapper, Optional.empty(),
                    DslProperties.bundleServiceDefaults()),
            null, providerOf(limitClient), null, null, null,
            providerOfBean(sourcePathResolver), providerOfBean(gitStatusResolver));
    for (int i = 1; i <= 4; i++) {
      handler.publish(postRequest("/api/dsl/drafts/X/publish", "X", String.valueOf(i)));
      Thread.sleep(2);
    }

    verify(limitClient, org.mockito.Mockito.times(4))
            .publishDraft(eq("X"), any(DraftRequest.class));
  }

  @Test
  void historyReturnsNewestFirstEntries() throws Exception {
    when(client.history("X")).thenReturn(List.of(
            new DefinitionHistoryEntry("abc", 2000L, 500L, 2000L)));

    ServerResponse response = handler.history(getRequest("/api/dsl/drafts/X/history",
            Map.of("name", "X")));

    assertThat(response.statusCode().value()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    List<DefinitionHistoryEntry> entries = (List<DefinitionHistoryEntry>) ((EntityResponse<?>) response)
            .entity();
    assertThat(entries).hasSize(1);
    DefinitionHistoryEntry entry = entries.get(0);
    assertThat(entry.timestamp()).isNotBlank();
    assertThat(entry.timestampMillis()).isGreaterThan(0L);
    assertThat(entry.sizeBytes()).isGreaterThan(0L);
    assertThat(entry.lastModifiedMillis()).isGreaterThan(0L);
    verify(client).history("X");
  }

  @Test
  void restoreRollsBackPublishedMetadata() throws Exception {
    when(client.history("X")).thenReturn(List.of(
            new DefinitionHistoryEntry("1000", 1000L, 500L, 1000L)));
    when(client.historyEntry("X", "1000")).thenReturn(
            new DraftRequest("X", "process", "Published", "A", null, "src-A", 1000L));
    when(client.restoreDraft("X", "1000")).thenReturn(
            new DraftResponse("X", "Published",
                    "/remote/.workbench/published/X.json", false, LoadResult.empty(),
                    null, null, 1000L, null));

    List<DefinitionHistoryEntry> entries = historyEntries("X");
    assertThat(entries).hasSize(1);
    String timestamp = entries.get(0).timestamp();

    Thread.sleep(2);
    ServerResponse response = handler.restore(getRequest(
            "/api/dsl/drafts/X/history/" + timestamp + "/restore",
            Map.of("name", "X", "timestamp", timestamp)));

    assertThat(response.statusCode().value()).isEqualTo(200);
    verify(client).restoreDraft("X", timestamp);
  }

  @Test
  void restoreUnknownTimestampReturns404() throws Exception {
    when(client.restoreDraft("X", "9999999999999"))
            .thenThrow(new BuilderApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "not found"));

    ServerResponse response = handler.restore(getRequest(
            "/api/dsl/drafts/X/history/9999999999999/restore",
            Map.of("name", "X", "timestamp", "9999999999999")));

    assertThat(response.statusCode().value()).isEqualTo(404);
  }

  @Test
  void restoreNonNumericTimestampReturns404() throws Exception {
    ServerResponse response = handler.restore(getRequest(
            "/api/dsl/drafts/X/history/evil/restore",
            Map.of("name", "X", "timestamp", "evil")));

    assertThat(response.statusCode().value()).isEqualTo(404);
  }

  @Test
  void historySanitizesTraversalName() throws Exception {
    when(client.history("../../etc")).thenReturn(List.of());

    ServerResponse response = handler.history(getRequest("/api/dsl/drafts/../../etc/history",
            Map.of("name", "../../etc")));

    assertThat(response.statusCode().value()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    List<DefinitionHistoryEntry> entries = (List<DefinitionHistoryEntry>) ((EntityResponse<?>) response)
            .entity();
    assertThat(entries).isEmpty();
  }

  @Test
  void restoreSanitizesTraversalName() throws Exception {
    ServerResponse response = handler.restore(getRequest(
            "/api/dsl/drafts/../../etc/history/123/restore",
            Map.of("name", "../../etc", "timestamp", "123")));

    assertThat(response.statusCode().value()).isEqualTo(404);
  }

  @Test
  void historyReturns409WhenSourceDirBlank() throws Exception {
    DslProperties blank = DslProperties.builder().sourceDir("").build();
    DslBuilderClient blankClient = mock(DslBuilderClient.class);
    stubLocalCompile(blankClient);
    handler = new DslDraftHandler(
            blank,
            new DslReloadHandler(blank, new DefinitionLoader(), null, null,
                    providerOf(blankClient), null, null, null),
            new DslDefinitionHistoryService(blank, mapper),
            mapper,
            new DslDefinitionBundleService(mapper, Optional.empty(),
                    DslProperties.bundleServiceDefaults()),
            null, providerOf(blankClient), null, null, null,
            providerOfBean(sourcePathResolver), providerOfBean(gitStatusResolver));

    ServerResponse response = handler.history(getRequest("/api/dsl/drafts/X/history",
            Map.of("name", "X")));

    assertThat(response.statusCode().value()).isEqualTo(409);
  }

  @Test
  void restoreReturns409WhenSourceDirBlank() throws Exception {
    DslProperties blank = DslProperties.builder().sourceDir("").build();
    DslBuilderClient blankClient = mock(DslBuilderClient.class);
    stubLocalCompile(blankClient);
    handler = new DslDraftHandler(
            blank,
            new DslReloadHandler(blank, new DefinitionLoader(), null, null,
                    providerOf(blankClient), null, null, null),
            new DslDefinitionHistoryService(blank, mapper),
            mapper,
            new DslDefinitionBundleService(mapper, Optional.empty(),
                    DslProperties.bundleServiceDefaults()),
            null, providerOf(blankClient), null, null, null,
            providerOfBean(sourcePathResolver), providerOfBean(gitStatusResolver));

    ServerResponse response = handler.restore(getRequest(
            "/api/dsl/drafts/X/history/123/restore",
            Map.of("name", "X", "timestamp", "123")));

    assertThat(response.statusCode().value()).isEqualTo(409);
  }

  @Test
  void historyEntryReturnsEntryContent() throws Exception {
    when(client.history("X")).thenReturn(List.of(
            new DefinitionHistoryEntry("1000", 1000L, 500L, 1000L)));
    when(client.historyEntry("X", "1000")).thenReturn(
            new DraftRequest("X", "process", "Published", "A", null, "src-A", 1000L));

    List<DefinitionHistoryEntry> entries = historyEntries("X");
    assertThat(entries).hasSize(1);
    String timestamp = entries.get(0).timestamp();

    ServerResponse response = handler.historyEntry(getRequest(
            "/api/dsl/drafts/X/history/" + timestamp,
            Map.of("name", "X", "timestamp", timestamp)));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DraftRequest entry = (DraftRequest) ((EntityResponse<?>) response).entity();
    assertThat(entry.name()).isEqualTo("X");
    assertThat(entry.version()).isEqualTo("A");
    assertThat(entry.status()).isEqualTo("Published");
  }

  @Test
  void historyEntryUnknownTimestampReturns404() throws Exception {
    when(client.historyEntry("X", "9999999999999"))
            .thenThrow(new BuilderApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "not found"));

    ServerResponse response = handler.historyEntry(getRequest(
            "/api/dsl/drafts/X/history/9999999999999",
            Map.of("name", "X", "timestamp", "9999999999999")));

    assertThat(response.statusCode().value()).isEqualTo(404);
  }

  @Test
  void historyDiffReturnsShapeWithHunks() throws Exception {
    when(client.history("X")).thenReturn(List.of(
            new DefinitionHistoryEntry("1000", 1000L, 500L, 1000L)));
    when(client.historyDiff("X", "1000")).thenReturn(
            new HistoryDiffResponse("X", "1000",
                    "{\"name\":\"X\",\"version\":\"B\",\"status\":\"Published\"}",
                    "{\"name\":\"X\",\"version\":\"A\",\"status\":\"Published\"}",
                    List.of(new cbs.nova.dsl.model.DiffHunk(1, 2, 1, 2,
                            List.of("@@ -1,2 +1,2 @@", "-\"version\":\"B\"",
                                    "+\"version\":\"A\""))),
                    false));

    List<DefinitionHistoryEntry> entries = historyEntries("X");
    assertThat(entries).hasSize(1);
    String timestamp = entries.get(0).timestamp();

    ServerResponse response = handler.historyDiff(getRequest(
            "/api/dsl/drafts/X/history/" + timestamp + "/diff",
            Map.of("name", "X", "timestamp", timestamp)));

    assertThat(response.statusCode().value()).isEqualTo(200);
    HistoryDiffResponse diff = (HistoryDiffResponse) ((EntityResponse<?>) response).entity();
    assertThat(diff.name()).isEqualTo("X");
    assertThat(diff.timestamp()).isEqualTo(timestamp);
    assertThat(diff.before()).contains("B");
    assertThat(diff.after()).contains("A");
    assertThat(diff.hunks()).isNotEmpty();
    assertThat(diff.hunks().stream().flatMap(h -> h.lines().stream()))
            .anyMatch(line -> line.startsWith("-") && line.contains("B"));
    assertThat(diff.hunks().stream().flatMap(h -> h.lines().stream()))
            .anyMatch(line -> line.startsWith("+") && line.contains("A"));
    assertThat(diff.truncated()).isFalse();
  }

  @Test
  void historyDiffWithoutPublishedReturnsNullBeforeAndNoHunks() throws Exception {
    when(client.history("X")).thenReturn(List.of(
            new DefinitionHistoryEntry("1000", 1000L, 500L, 1000L)));
    when(client.historyDiff("X", "1000")).thenReturn(
            new HistoryDiffResponse("X", "1000", null,
                    "{\"name\":\"X\",\"version\":\"A\",\"status\":\"Published\"}",
                    List.of(), false));
    List<DefinitionHistoryEntry> entries = historyEntries("X");
    String timestamp = entries.get(0).timestamp();

    ServerResponse response = handler.historyDiff(getRequest(
            "/api/dsl/drafts/X/history/" + timestamp + "/diff",
            Map.of("name", "X", "timestamp", timestamp)));

    assertThat(response.statusCode().value()).isEqualTo(200);
    HistoryDiffResponse diff = (HistoryDiffResponse) ((EntityResponse<?>) response).entity();
    assertThat(diff.before()).isNull();
    assertThat(diff.after()).isNotNull();
    assertThat(diff.hunks()).isEmpty();
    assertThat(diff.truncated()).isFalse();
  }

  @Test
  void historyDiffEmptyHistoryReturns404() throws Exception {
    when(client.historyDiff("X", "123"))
            .thenThrow(new BuilderApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "not found"));

    ServerResponse response = handler.historyDiff(getRequest(
            "/api/dsl/drafts/X/history/123/diff",
            Map.of("name", "X", "timestamp", "123")));

    assertThat(response.statusCode().value()).isEqualTo(404);
  }

  private List<DefinitionHistoryEntry> historyEntries(String name) throws Exception {
    ServerResponse response = handler.history(getRequest("/api/dsl/drafts/" + name + "/history",
            Map.of("name", name)));
    @SuppressWarnings("unchecked")
    List<DefinitionHistoryEntry> entries = (List<DefinitionHistoryEntry>) ((EntityResponse<?>) response)
            .entity();
    return entries;
  }

  private void deleteRecursively(Path path) throws IOException {
    try (Stream<Path> stream = Files.walk(path)) {
      stream.sorted((a, b) -> -a.compareTo(b)).forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (IOException e) {
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

  private static final class StringBodyHttpMessageConverter
          implements
            HttpMessageConverter<String> {

    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
      return String.class.isAssignableFrom(clazz);
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
    public String read(Class<? extends String> clazz, HttpInputMessage inputMessage)
            throws IOException {
      return new String(inputMessage.getBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Override
    public void write(String string, MediaType contentType, HttpOutputMessage outputMessage) {
      throw new UnsupportedOperationException();
    }
  }

  @Test
  void saveDelegatesToBuilderClient() throws Exception {
    DslBuilderClient testClient = mock(DslBuilderClient.class);
    stubSuccessfulCompile(testClient);
    when(testClient.saveDraft(eq("foo"), any())).thenReturn(
            new DraftResponse("foo", "Draft", "/remote/.workbench/drafts/foo.json", false,
                    LoadResult.empty(), null, null, null, null));
    handler = builderDraftHandler(testClient);

    ServerResponse response = handler.save(postRequest("/api/dsl/drafts/foo/save"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DraftResponse body = (DraftResponse) ((EntityResponse<?>) response).entity();
    assertThat(body.location()).isEqualTo("/remote/.workbench/drafts/foo.json");
    verify(testClient).saveDraft(eq("foo"), any());
    assertThat(sourceDir.resolve(".workbench/drafts/foo.json")).doesNotExist();
  }

  @Test
  void publishDelegatesToBuilderClientAndReloadsLocally() throws Exception {
    DslBuilderClient testClient = mock(DslBuilderClient.class);
    stubSuccessfulCompile(testClient);
    when(testClient.publishDraft(eq("foo"), any())).thenReturn(
            new DraftResponse("foo", "Published", "/remote/.workbench/published/foo.json", false,
                    LoadResult.empty(), null, null, null, null));
    handler = builderDraftHandler(testClient);

    ServerResponse response = handler.publish(postRequest("/api/dsl/drafts/foo/publish"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DraftResponse body = (DraftResponse) ((EntityResponse<?>) response).entity();
    assertThat(body.location()).isEqualTo("/remote/.workbench/published/foo.json");
    assertThat(body.reloaded()).isTrue();
    assertThat(body.reloadError()).isNull();
    assertThat(sourceDir.resolve(".workbench/published/foo.json")).doesNotExist();
  }

  @Test
  void publishSurfacesReloadFailureWhenBuilderCompileFails() throws Exception {
    Files.writeString(sourceDir.resolve("Broken.java"),
            "this is not valid Java at all; { class Broken { ???");
    DslBuilderClient testClient = mock(DslBuilderClient.class);
    stubSuccessfulCompile(testClient);
    when(testClient.compile(any())).thenThrow(new DslCompilationException("DSL compilation failed",
            List.of(new CompileDiagnostic("Broken.java", 1L, null, "bad syntax", "error", null))));
    when(testClient.publishDraft(eq("foo"), any())).thenReturn(
            new DraftResponse("foo", "Published", "/remote/.workbench/published/foo.json", false,
                    LoadResult.empty(), null, null, null, null));
    handler = builderDraftHandler(testClient);

    ServerResponse response = handler.publish(postRequest("/api/dsl/drafts/foo/publish"));

    DraftResponse body = (DraftResponse) ((EntityResponse<?>) response).entity();
    assertThat(body.reloaded()).isFalse();
    assertThat(body.reloadError()).contains("DSL compilation failed");
    assertThat(body.diagnostics()).isNotEmpty();
    assertThat(body.diagnostics().get(0).file()).isEqualTo("Broken.java");
  }

  @Test
  void deleteDelegatesToBuilderClient() throws Exception {
    DslBuilderClient testClient = mock(DslBuilderClient.class);
    when(testClient.deleteDraft("foo")).thenReturn(
            new DraftResponse("foo", "Deleted", null, false, LoadResult.empty(), null, null, null,
                    null));
    handler = builderDraftHandler(testClient);

    ServerResponse response = handler.delete(deleteRequest("foo", "/api/dsl/drafts/foo"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    verify(testClient).deleteDraft("foo");
  }

  @Test
  void listDelegatesToBuilderClient() throws Exception {
    DslBuilderClient testClient = mock(DslBuilderClient.class);
    when(testClient.listDrafts(50, 0)).thenReturn(
            new PageResponse<>(List.of(new DraftSummary("foo", "process", "Draft", "1", 42)), 1,
                    0, 50));
    handler = builderDraftHandler(testClient);

    ServerResponse response = handler.list(getRequest("/api/dsl/drafts", null));

    @SuppressWarnings("unchecked")
    PageResponse<DraftSummary> body = (PageResponse<DraftSummary>) ((EntityResponse<?>) response)
            .entity();
    assertThat(body.items()).hasSize(1);
    assertThat(body.items().get(0).name()).isEqualTo("foo");
  }

  private DslDraftHandler builderDraftHandler(DslBuilderClient testClient) {
    return new DslDraftHandler(props,
            new DslReloadHandler(props, new DefinitionLoader(), null, null, providerOf(testClient),
                    null, null, null),
            new DslDefinitionHistoryService(props, mapper), mapper,
            new DslDefinitionBundleService(mapper, Optional.empty(),
                    DslProperties.bundleServiceDefaults()),
            null, providerOf(testClient),
            null, null, null,
            providerOfBean(sourcePathResolver), providerOfBean(gitStatusResolver));
  }

  @Test
  void saveWritesAuditRowOnSuccess() throws Exception {
    var audit = AuditTestSupport.h2();
    DslDraftHandler audited = auditedDraftHandler(audit, props);

    ServerResponse response = audited.save(postRequest("/api/dsl/drafts/foo/save"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    var result = audit.service().search(null, 0, 10);
    assertThat(result.total()).isEqualTo(1);
    var row = result.items().get(0);
    assertThat(row.action()).isEqualTo("DRAFT_WRITE");
    assertThat(row.outcome()).isEqualTo("SUCCESS");
    assertThat(row.target()).isEqualTo("foo");
    assertThat(row.actor()).isEqualTo("anonymous");
    assertThat(row.correlationId()).isNull();
  }

  @Test
  void saveWritesAuditRowWithCorrelationIdOnFailure() throws Exception {
    var audit = AuditTestSupport.h2();
    DslProperties blank = DslProperties.builder().sourceDir("").build();
    DslDraftHandler audited = auditedDraftHandler(audit, blank);

    ServerRequest request = postRequestWithHeader("/api/dsl/drafts/foo/save", "foo",
            "X-Correlation-Id", "corr-draft-1");
    ServerResponse response = audited.save(request);

    assertThat(response.statusCode().value()).isEqualTo(409);
    var result = audit.service().search(null, 0, 10);
    assertThat(result.total()).isEqualTo(1);
    var row = result.items().get(0);
    assertThat(row.action()).isEqualTo("DRAFT_WRITE");
    assertThat(row.outcome()).isEqualTo("FAILURE");
    assertThat(row.target()).isEqualTo("foo");
    assertThat(row.correlationId()).isEqualTo("corr-draft-1");
  }

  @Test
  void publishWritesAuditRowOnSuccess() throws Exception {
    GlobalManager.globalManager().resetForTests();
    try {
      var audit = AuditTestSupport.h2();
      DslBuilderClient auditClient = mock(DslBuilderClient.class);
      stubLocalCompile(auditClient);
      lenient().when(auditClient.publishDraft(anyString(), any(DraftRequest.class)))
              .thenAnswer(inv -> {
                String name = inv.getArgument(0);
                return new DraftResponse(name, "Published",
                        "/remote/.workbench/published/" + name + ".json", false,
                        LoadResult.empty(), null, null, null, null);
              });
      DslDraftHandler audited = new DslDraftHandler(props,
              new DslReloadHandler(props, new DefinitionLoader(), null, null,
                      providerOf(auditClient), null, null, null),
              new DslDefinitionHistoryService(props, mapper), mapper,
              new DslDefinitionBundleService(mapper, Optional.empty(),
                      DslProperties.bundleServiceDefaults()),
              AuditTestSupport.providerOf(audit.service()), providerOf(auditClient),
              null, null, null,
              providerOfBean(sourcePathResolver), providerOfBean(gitStatusResolver));

      ServerResponse response = audited.publish(postRequest("/api/dsl/drafts/foo/publish"));

      assertThat(response.statusCode().value()).isEqualTo(200);
      var result = audit.service().search(null, 0, 10);
      assertThat(result.total()).isEqualTo(1);
      var row = result.items().get(0);
      assertThat(row.action()).isEqualTo("DEFINITION_PUBLISH");
      assertThat(row.outcome()).as(row.detailsJson()).isEqualTo("SUCCESS");
      assertThat(row.target()).isEqualTo("foo");
    } finally {
      GlobalManager.globalManager().resetForTests();
    }
  }

  private DslDraftHandler auditedDraftHandler(AuditTestSupport.Harness audit,
          DslProperties properties) {
    DslBuilderClient auditClient = mock(DslBuilderClient.class);
    stubLocalCompile(auditClient);
    lenient().when(auditClient.saveDraft(anyString(), any(DraftRequest.class)))
            .thenAnswer(inv -> {
              String name = inv.getArgument(0);
              return new DraftResponse(name, "Draft",
                      "/remote/.workbench/drafts/" + name + ".json", false,
                      LoadResult.empty(), null, null, System.currentTimeMillis(), null);
            });
    lenient().when(auditClient.publishDraft(anyString(), any(DraftRequest.class)))
            .thenAnswer(inv -> {
              String name = inv.getArgument(0);
              return new DraftResponse(name, "Published",
                      "/remote/.workbench/published/" + name + ".json", false,
                      LoadResult.empty(), null, null, null, null);
            });
    return new DslDraftHandler(properties,
            new DslReloadHandler(properties, new DefinitionLoader(), null, null,
                    providerOf(auditClient), null, null, null),
            new DslDefinitionHistoryService(properties, mapper), mapper,
            new DslDefinitionBundleService(mapper, Optional.empty(),
                    DslProperties.bundleServiceDefaults()),
            AuditTestSupport.providerOf(audit.service()), providerOf(auditClient),
            null, null, null,
            providerOfBean(sourcePathResolver), providerOfBean(gitStatusResolver));
  }

  private static ServerRequest postRequestWithHeader(String path, String name, String header,
          String value) {
    var req = new MockHttpServletRequest("POST", path);
    req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("name", name));
    req.setContentType("application/json");
    req.addHeader(header, value);
    req.setContent(
            ("{\"name\":\"" + name
                    + "\",\"type\":\"process\",\"status\":\"Draft\",\"version\":\"1\"}")
                    .getBytes());
    return ServerRequest.create(req, CONVERTERS);
  }

  @Configuration
  @EnableConfigurationProperties(DslProperties.class)
  static class DslDraftTestConfig {

    @Bean
    DslReloadHandler dslReloadHandler(DslProperties props) {
      return new DslReloadHandler(props, null, null, null, null, null, null, null);
    }

    @Bean
    DslDefinitionHistoryService dslDefinitionHistoryService(DslProperties props,
            ObjectMapper mapper) {
      return new DslDefinitionHistoryService(props, mapper);
    }

    @Bean
    DslDefinitionBundleService dslDefinitionBundleService(ObjectMapper mapper) {
      return new DslDefinitionBundleService(mapper, Optional.empty(),
              DslProperties.bundleServiceDefaults());
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }

  static List<HttpMessageConverter<?>> converters() {
    return CONVERTERS;
  }
}
