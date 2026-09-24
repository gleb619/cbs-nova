package cbs.nova.starter;

import static cbs.nova.starter.BuilderClientTestSupport.providerOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import cbs.nova.dsl.utils.DefinitionLoader;
import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.DslDraftHandler;
import cbs.nova.starter.controller.DslReloadHandler;
import cbs.nova.starter.model.VcsModels.DraftsMetadata;
import cbs.nova.starter.service.DslDefinitionBundleService;
import cbs.nova.starter.service.DslDefinitionHistoryService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
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
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    DslBuilderClient client = mock(DslBuilderClient.class);
    DslProperties props = DslProperties.builder().sourceDir(sourceDir.toString()).build();
    DslReloadHandler reloadHandler = new DslReloadHandler(props,
            new DefinitionLoader(), null, null, providerOf(client), null, null, null);
    handler = new DslDraftHandler(props, reloadHandler,
            new DslDefinitionHistoryService(props, mapper), mapper,
            new DslDefinitionBundleService(mapper, Optional.empty(),
                    DslProperties.bundleServiceDefaults()),
            null, providerOf(client), null, null, null, null, null);
  }

  @Test
  void returnsZeroCountWhenDraftsDirDoesNotExist() throws Exception {
    ServerResponse response = handler.metadata(getRequest("/api/dsl/drafts/metadata"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DraftsMetadata body = entity(response);
    assertThat(body.draftCount()).isZero();
    assertThat(body.sizeMb()).isNull();
  }

  @Test
  void countsJsonFilesInWorkbenchDraftsDir() throws Exception {
    Path draftsDir = sourceDir.resolve(".workbench").resolve("drafts");
    Files.createDirectories(draftsDir);
    Files.writeString(draftsDir.resolve("alpha.json"), "{\"name\":\"alpha\"}",
            StandardCharsets.UTF_8);
    Files.writeString(draftsDir.resolve("beta.json"), "{\"name\":\"beta\"}",
            StandardCharsets.UTF_8);
    Files.writeString(draftsDir.resolve("readme.txt"), "not a draft");

    ServerResponse response = handler.metadata(getRequest("/api/dsl/drafts/metadata"));

    DraftsMetadata body = entity(response);
    assertThat(body.draftCount()).isEqualTo(2);
  }

  @Test
  void reportsSizeMbSumOfDraftFiles() throws Exception {
    Path draftsDir = sourceDir.resolve(".workbench").resolve("drafts");
    Files.createDirectories(draftsDir);
    byte[] content = "x".repeat(1024).getBytes(StandardCharsets.UTF_8);
    Files.write(draftsDir.resolve("a.json"), content);
    Files.write(draftsDir.resolve("b.json"), content);

    DraftsMetadata body = entity(handler.metadata(getRequest("/api/dsl/drafts/metadata")));

    assertThat(body.sizeMb()).isNotNull();
    assertThat(body.sizeMb()).isGreaterThanOrEqualTo(0.0);
  }

  @Test
  void workbenchPathIsRelativeAndUsesForwardSlashes() throws Exception {
    DraftsMetadata body = entity(handler.metadata(getRequest("/api/dsl/drafts/metadata")));

    assertThat(body.workbenchPath()).doesNotContain("\\");
    assertThat(body.workbenchPath()).doesNotStartWith("/");
    assertThat(Path.of(body.workbenchPath()).isAbsolute()).isFalse();
  }

  @Test
  void gitIsDisabledByDefault() throws Exception {
    DraftsMetadata body = entity(handler.metadata(getRequest("/api/dsl/drafts/metadata")));

    assertThat(body.gitEnabled()).isTrue();
    assertThat(body.gitBranch()).isNull();
  }

  @Test
  void returnsConfiguredHistoryLimitAndCacheTtl() throws Exception {
    DslProperties customProps = DslProperties.builder()
            .sourceDir(sourceDir.toString())
            .drafts(DslProperties.Drafts.builder().historyLimit(42).build())
            .git(DslProperties.Git.builder().statusCacheTtlSeconds(15).build())
            .build();
    DslBuilderClient client = mock(DslBuilderClient.class);
    DslReloadHandler reloadHandler = new DslReloadHandler(customProps,
            new DefinitionLoader(), null, null, providerOf(client), null, null, null);
    DslDraftHandler customHandler = new DslDraftHandler(customProps, reloadHandler,
            new DslDefinitionHistoryService(customProps, mapper), mapper,
            new DslDefinitionBundleService(mapper, Optional.empty(),
                    DslProperties.bundleServiceDefaults()),
            null, providerOf(client), null, null, null, null, null);

    DraftsMetadata body = entity(
            customHandler.metadata(getRequest("/api/dsl/drafts/metadata")));

    assertThat(body.historyLimit()).isEqualTo(42);
    assertThat(body.statusCacheTtlSeconds()).isEqualTo(15);
  }

  @Test
  void returnsNotConfiguredFallbackWhenSourceDirBlank() throws Exception {
    DslProperties noDir = DslProperties.builder().sourceDir("").build();
    DslBuilderClient client = mock(DslBuilderClient.class);
    DslReloadHandler reloadHandler = new DslReloadHandler(noDir,
            new DefinitionLoader(), null, null, providerOf(client), null, null, null);
    DslDraftHandler unconfiguredHandler = new DslDraftHandler(noDir, reloadHandler,
            new DslDefinitionHistoryService(noDir, mapper), mapper,
            new DslDefinitionBundleService(mapper, Optional.empty(),
                    DslProperties.bundleServiceDefaults()),
            null, providerOf(client), null, null, null, null, null);

    DraftsMetadata body = entity(
            unconfiguredHandler.metadata(getRequest("/api/dsl/drafts/metadata")));

    assertThat(body.draftCount()).isZero();
    assertThat(body.workbenchPath()).isNotBlank();
  }

  @SuppressWarnings("unchecked")
  private static DraftsMetadata entity(ServerResponse response) {
    return (DraftsMetadata) ((EntityResponse<?>) response).entity();
  }

  private static ServerRequest getRequest(String path) {
    var req = new MockHttpServletRequest("GET", path);
    req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, java.util.Map.of());
    return ServerRequest.create(req, DslDraftResourceTest.converters());
  }
}
