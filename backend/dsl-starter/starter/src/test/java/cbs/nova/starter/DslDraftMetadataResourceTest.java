package cbs.nova.starter;

import static cbs.nova.starter.BuilderClientTestSupport.providerOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.utils.DefinitionLoader;
import cbs.nova.dsl.vcs.RepoStatus;
import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.DslDraftHandler;
import cbs.nova.starter.controller.DslReloadHandler;
import cbs.nova.starter.model.DslFileModels.FileEntry;
import cbs.nova.starter.model.VcsModels.DraftsMetadata;
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

  @BeforeEach
  void setUp() {
    client = mock(DslBuilderClient.class);
    gitStatusResolver = mock(DslGitStatusResolver.class);
    DslProperties props = DslProperties.builder().sourceDir(sourceDir.toString()).build();
    DslReloadHandler reloadHandler = new DslReloadHandler(props,
            new DefinitionLoader(), null, null, providerOf(client), null, null, null);
    handler = new DslDraftHandler(props, reloadHandler, mapper,
            bundleService(props),
            null, providerOf(client), null, null, null,
            null, providerOfBean(gitStatusResolver), null);
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
    DslDraftHandler repoHandler = new DslDraftHandler(repoProps, reloadHandler, mapper,
            new DslDefinitionBundleService(mapper, Optional.empty(), repoProps,
                    new DslSourcePathResolver(repoProps), providerOf(client)),
            null, providerOf(client), null, null, null,
            null, providerOfBean(gitStatusResolver), null);
    RepoStatus status = RepoStatus.of(repoRoot, new LinkedHashMap<>(Map.of(
            "dsl/LoanDsl.java", cbs.nova.dsl.vcs.ChangeType.MODIFIED,
            "dsl/Other.java", cbs.nova.dsl.vcs.ChangeType.UNTRACKED,
            "outside/x.java", cbs.nova.dsl.vcs.ChangeType.UNTRACKED)));
    when(gitStatusResolver.status(any())).thenReturn(Optional.of(status));
    when(client.listFiles(isNull())).thenReturn(List.of(
            new FileEntry("dsl/LoanDsl.java", 2L * 1024 * 1024, 0),
            new FileEntry("dsl/Other.java", 1024 * 1024, 0)));

    DraftsMetadata body = entity(repoHandler.metadata(getRequest("/api/dsl/drafts/metadata")));

    assertThat(body.draftCount()).isEqualTo(2);
    assertThat(body.sizeMb()).isNotNull();
    assertThat(body.sizeMb()).isGreaterThan(0.0);
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
    DslDraftHandler customHandler = new DslDraftHandler(customProps, reloadHandler, mapper,
            bundleService(customProps),
            null, providerOf(client), null, null, null,
            null, providerOfBean(gitStatusResolver), null);

    DraftsMetadata body = entity(
            customHandler.metadata(getRequest("/api/dsl/drafts/metadata")));

    assertThat(body.statusCacheTtlSeconds()).isEqualTo(15);
  }

  @Test
  void historyLimitExposesSharedHistoryConstant() throws Exception {
    when(gitStatusResolver.status(any())).thenReturn(Optional.empty());

    DraftsMetadata body = entity(handler.metadata(getRequest("/api/dsl/drafts/metadata")));

    assertThat(body.historyLimit()).isEqualTo(DslDraftHandler.HISTORY_LIMIT);
  }

  @Test
  void returnsZeroDraftsWhenSourceDirBlank() throws Exception {
    when(gitStatusResolver.status(any())).thenReturn(Optional.empty());
    DslProperties noDir = DslProperties.builder().sourceDir("").build();
    DslReloadHandler reloadHandler = new DslReloadHandler(noDir,
            new DefinitionLoader(), null, null, providerOf(client), null, null, null);
    DslDraftHandler unconfiguredHandler = new DslDraftHandler(noDir, reloadHandler, mapper,
            bundleService(noDir),
            null, providerOf(client), null, null, null,
            null, providerOfBean(gitStatusResolver), null);

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
  private static <T> org.springframework.beans.factory.ObjectProvider<T> providerOfBean(T bean) {
    org.springframework.beans.factory.ObjectProvider<T> provider = mock(
            org.springframework.beans.factory.ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(bean);
    return provider;
  }

  @SuppressWarnings("unchecked")
  private static DraftsMetadata entity(ServerResponse response) {
    return (DraftsMetadata) ((EntityResponse<?>) response).entity();
  }

  private static ServerRequest getRequest(String path) {
    var req = new MockHttpServletRequest("GET", path);
    req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, java.util.Map.of());
    return ServerRequest.create(req, List.of(
            new org.springframework.http.converter.ByteArrayHttpMessageConverter(),
            new org.springframework.http.converter.StringHttpMessageConverter()));
  }
}
