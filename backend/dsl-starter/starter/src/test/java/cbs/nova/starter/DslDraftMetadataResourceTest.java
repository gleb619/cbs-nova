package cbs.nova.starter;

import static cbs.nova.starter.BuilderClientTestSupport.providerOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.utils.DefinitionLoader;
import cbs.nova.dsl.vcs.ChangeType;
import cbs.nova.dsl.vcs.RepoStatus;
import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.DslDraftHandler;
import cbs.nova.starter.controller.DslDraftSupport;
import cbs.nova.starter.controller.DslReloadHandler;
import cbs.nova.starter.model.VcsModels.DraftsMetadata;
import cbs.nova.starter.model.VcsModels.WorkspaceInfo;
import cbs.nova.starter.service.DslDefinitionBundleService;
import cbs.nova.starter.service.DslGitStatusResolver;
import cbs.nova.starter.service.DslSourcePathResolver;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.EntityResponse;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

class DslDraftMetadataResourceTest {

  @TempDir
  Path sourceDir;

  private DslDraftHandler handler;
  private DslBuilderClient client;
  private DslGitStatusResolver gitStatusResolver;
  private final ObjectMapper mapper = new ObjectMapper();

  private DslDraftSupport support;

  @BeforeEach
  void setUp() {
    client = mock(DslBuilderClient.class);
    gitStatusResolver = mock(DslGitStatusResolver.class);
    DslProperties props = DslProperties.builder().sourceDir(sourceDir.toString()).build();
    DslReloadHandler reloadHandler = new DslReloadHandler(props,
            new DefinitionLoader(), null, null, providerOf(client), null, null, null);
    support = new DslDraftSupport(props, providerOf(client), null, null, null);
    handler = new DslDraftHandler(props, reloadHandler, mapper,
            bundleService(props),
            null, providerOf(client), null, null, null,
            null, providerOfBean(gitStatusResolver), null, support);
  }

  @Test
  void reportsZeroDraftsWhenGitIsUnavailable() throws Exception {
    when(gitStatusResolver.status(any())).thenReturn(Optional.empty());

    DraftsMetadata body = entity(handler.metadata(getRequest("/api/dsl/drafts/metadata")));

    assertThat(body.draftCount()).isZero();
    assertThat(body.sizeMb()).isNull();
  }

  @Test
  void countsChangedPathsRestrictedToSourceRoot() throws Exception {
    // The repo root is the parent of the source dir, and `outside/` lives next to `sourceDir`
    // so the filter can reject paths outside the source root. We rebuild the handler against a
    // dedicated workspace so we can pin the source dir as a subdirectory of the repo root.
    Path repoRoot = Files.createTempDirectory("metadata-repo-");
    Path sourceRoot = Files.createDirectory(repoRoot.resolve("dsl"));
    Path outside = Files.createDirectory(repoRoot.resolve("outside"));
    Files.writeString(outside.resolve("x.java"), "class X {}", StandardCharsets.UTF_8);
    DslProperties repoProps = DslProperties.builder().sourceDir(sourceRoot.toString()).build();
    DslReloadHandler reloadHandler = new DslReloadHandler(repoProps,
            new DefinitionLoader(), null, null, providerOf(client), null, null, null);
    support = new DslDraftSupport(repoProps, providerOf(client), null, null, null);
    DslDraftHandler repoHandler = new DslDraftHandler(repoProps, reloadHandler, mapper,
            new DslDefinitionBundleService(mapper, Optional.empty(), repoProps,
                    new DslSourcePathResolver(repoProps), providerOf(client)),
            null, providerOf(client), null, null, null,
            null, providerOfBean(gitStatusResolver), null, support);
    RepoStatus status = RepoStatus.of(repoRoot,
            new LinkedHashMap<>(Map.of(
                    "dsl/LoanDsl.java", ChangeType.MODIFIED,
                    "dsl/Other.java", ChangeType.UNTRACKED,
                    "outside/x.java", ChangeType.UNTRACKED)),
            new LinkedHashMap<>(Map.<String, Long>of(
                    "dsl/LoanDsl.java", 2L * 1024 * 1024,
                    "dsl/Other.java", 1024L * 1024,
                    "outside/x.java", 4096L)));
    when(gitStatusResolver.status(any())).thenReturn(Optional.of(status));

    DraftsMetadata body = entity(repoHandler.metadata(getRequest("/api/dsl/drafts/metadata")));

    assertThat(body.draftCount()).isEqualTo(2);
    assertThat(body.sizeMb()).isEqualTo(3.0);
  }

  @Test
  void reportsZeroSizeWhenThereAreNoDrafts() throws Exception {
    when(gitStatusResolver.status(any()))
            .thenReturn(Optional.of(RepoStatus.of(sourceDir, Map.of())));

    DraftsMetadata body = entity(handler.metadata(getRequest("/api/dsl/drafts/metadata")));

    assertThat(body.draftCount()).isZero();
    assertThat(body.sizeMb()).isZero();
  }

  @Test
  void sizeMbIsNullWhenStatusCarriesNoSizes() throws Exception {
    when(gitStatusResolver.status(any()))
            .thenReturn(Optional.of(RepoStatus.of(sourceDir,
                    Map.of("dsl/FooDsl.java", ChangeType.MODIFIED))));

    DraftsMetadata body = entity(handler.metadata(getRequest("/api/dsl/drafts/metadata")));

    assertThat(body.draftCount()).isEqualTo(1);
    assertThat(body.sizeMb()).isNull();
  }

  @Test
  void reportsSourcePathWithForwardSlashes() throws Exception {
    when(gitStatusResolver.status(any())).thenReturn(Optional.empty());

    DraftsMetadata body = entity(handler.metadata(getRequest("/api/dsl/drafts/metadata")));

    assertThat(body.sourcePath()).doesNotContain("\\");
    assertThat(body.sourcePath()).startsWith("/");
    assertThat(Path.of(body.sourcePath())).isAbsolute();
  }

  @Test
  void sharedModeReportsBuilderWorkTreeAsSourcePath() throws Exception {
    Path builderDir = Files.createDirectory(sourceDir.resolve("builder-shared"));
    when(gitStatusResolver.status(any())).thenReturn(Optional.empty());
    when(client.workspace()).thenReturn(new WorkspaceInfo("SHARED", builderDir.toString()));

    DraftsMetadata body = entity(handler.metadata(getRequest("/api/dsl/drafts/metadata")));

    assertThat(body.sourcePath()).isEqualTo(builderDir.toString().replace('\\', '/'));
    assertThat(body.workspaceMode()).isEqualTo("SHARED");
  }

  @Test
  void worktreeModeReportsBuilderWorkTreeAndMode() throws Exception {
    Path worktree = Files.createTempDirectory("metadata-worktree-");
    when(gitStatusResolver.status(any())).thenReturn(Optional.empty());
    when(client.workspace()).thenReturn(new WorkspaceInfo("WORKTREE", worktree.toString()));

    DraftsMetadata body = entity(handler.metadata(getRequest("/api/dsl/drafts/metadata")));

    assertThat(body.sourcePath()).isEqualTo(worktree.toString().replace('\\', '/'));
    assertThat(body.workspaceMode()).isEqualTo("WORKTREE");
  }

  @Test
  void builderWorkspaceFailureFallsBackToConfiguredSourceDir() throws Exception {
    when(gitStatusResolver.status(any())).thenReturn(Optional.empty());
    when(client.workspace()).thenThrow(new RuntimeException("builder down"));

    DraftsMetadata body = entity(handler.metadata(getRequest("/api/dsl/drafts/metadata")));

    assertThat(body.sourcePath()).isEqualTo(sourceDir.toString().replace('\\', '/'));
    assertThat(body.workspaceMode()).isNull();
  }

  @Test
  void reportsBranchAndDisabledFlag() throws Exception {
    when(gitStatusResolver.status(any())).thenReturn(Optional.empty());
    when(client.vcsBranch()).thenReturn("main");

    DraftsMetadata body = entity(handler.metadata(getRequest("/api/dsl/drafts/metadata")));

    assertThat(body.gitEnabled()).isTrue();
    assertThat(body.gitBranch()).isEqualTo("main");
  }

  @Test
  void branchLookupFailureLeavesBranchNull() throws Exception {
    when(gitStatusResolver.status(any())).thenReturn(Optional.empty());
    when(client.vcsBranch()).thenThrow(new RuntimeException("builder down"));

    DraftsMetadata body = entity(handler.metadata(getRequest("/api/dsl/drafts/metadata")));

    assertThat(body.gitBranch()).isNull();
  }

  @Test
  void statusCacheTtlSecondsFlowsFromProperties() throws Exception {
    when(gitStatusResolver.status(any())).thenReturn(Optional.empty());
    DslProperties customProps = DslProperties.builder()
            .sourceDir(sourceDir.toString())
            .git(DslProperties.Git.builder().statusCacheTtlSeconds(15).build())
            .build();
    DslReloadHandler reloadHandler = new DslReloadHandler(customProps,
            new DefinitionLoader(), null, null, providerOf(client), null, null, null);
    support = new DslDraftSupport(customProps, providerOf(client), null, null, null);
    DslDraftHandler customHandler = new DslDraftHandler(customProps, reloadHandler, mapper,
            bundleService(customProps),
            null, providerOf(client), null, null, null,
            null, providerOfBean(gitStatusResolver), null, support);

    DraftsMetadata body = entity(
            customHandler.metadata(getRequest("/api/dsl/drafts/metadata")));

    assertThat(body.statusCacheTtlSeconds()).isEqualTo(15);
  }

  @Test
  void jsonDoesNotExposeHistoryLimit() throws Exception {
    when(gitStatusResolver.status(any())).thenReturn(Optional.empty());

    DraftsMetadata body = entity(handler.metadata(getRequest("/api/dsl/drafts/metadata")));
    String json = mapper.writeValueAsString(body);

    assertThat(json).doesNotContain("historyLimit");
  }

  @Test
  void returnsZeroDraftsWhenSourceDirBlank() throws Exception {
    when(gitStatusResolver.status(any())).thenReturn(Optional.empty());
    DslProperties noDir = DslProperties.builder().sourceDir("").build();
    DslReloadHandler reloadHandler = new DslReloadHandler(noDir,
            new DefinitionLoader(), null, null, providerOf(client), null, null, null);
    support = new DslDraftSupport(noDir, providerOf(client), null, null, null);
    DslDraftHandler unconfiguredHandler = new DslDraftHandler(noDir, reloadHandler, mapper,
            bundleService(noDir),
            null, providerOf(client), null, null, null,
            null, providerOfBean(gitStatusResolver), null, support);

    DraftsMetadata body = entity(
            unconfiguredHandler.metadata(getRequest("/api/dsl/drafts/metadata")));

    assertThat(body.draftCount()).isZero();
    assertThat(body.sourcePath()).isEmpty();
  }

  private DslDefinitionBundleService bundleService(DslProperties props) {
    DslSourcePathResolver resolver = new DslSourcePathResolver(props);
    return new DslDefinitionBundleService(mapper, Optional.empty(), props, resolver,
            providerOf(client));
  }

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> providerOfBean(T bean) {
    ObjectProvider<T> provider = mock(
            ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(bean);
    return provider;
  }

  @SuppressWarnings("unchecked")
  private static DraftsMetadata entity(ServerResponse response) {
    return (DraftsMetadata) ((EntityResponse<?>) response).entity();
  }

  private static ServerRequest getRequest(String path) {
    var req = new MockHttpServletRequest("GET", path);
    req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of());
    return ServerRequest.create(req, List.of(
            new ByteArrayHttpMessageConverter(),
            new StringHttpMessageConverter()));
  }
}
