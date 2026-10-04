package cbs.nova.starter.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.model.LoadResult;
import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.model.VcsModels.DefinitionBundle;
import cbs.nova.starter.model.VcsModels.DefinitionBundleEntry;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import cbs.nova.starter.model.VcsModels.ImportBundleResult;
import cbs.nova.starter.model.VcsModels.ImportEntryResult;
import cbs.nova.starter.service.DslDefinitionBundleService;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.EntityResponse;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit spec for {@link DslDraftHandler} export/import bundle param mapping. Mocks
 * {@link DslDefinitionBundleService} so only the handler's own parameter interpretation is
 * exercised.
 */
class DslDraftHandlerTest {

  private Path sourceDir;
  private DslProperties props;
  private DslDraftHandler handler;
  private DslDefinitionBundleService bundleService;
  private DslReloadHandler reloadHandler;
  private DslBuilderClient builderClient;
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() throws IOException {
    sourceDir = Files.createTempDirectory("dsl-draft-handler-test-");
    props = DslProperties.builder().sourceDir(sourceDir.toString()).build();
    bundleService = mock(DslDefinitionBundleService.class);
    reloadHandler = mock(DslReloadHandler.class);
    builderClient = mock(DslBuilderClient.class);

    @SuppressWarnings("unchecked")
    ObjectProvider<DslBuilderClient> builderProvider = mock(ObjectProvider.class);
    when(builderProvider.getIfAvailable()).thenReturn(builderClient);

    DslDraftSupport support = new DslDraftSupport(props, builderProvider, null, null, null);
    handler = new DslDraftHandler(props, reloadHandler, mapper, bundleService,
            null, builderProvider, null, null, null,
            null, null, null, support);
  }

  @AfterEach
  void tearDown() throws IOException {
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
  void exportWithoutIncludeParamCallsExportWithFalse() throws Exception {
    DefinitionBundle expected = emptyBundle();
    when(bundleService.export(eq(sourceDir), eq(false))).thenReturn(expected);

    ServerResponse response = handler.exportBundle(getRequest("/api/dsl/definitions/export"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DefinitionBundle actual = entityOf(response);
    assertThat(actual).isSameAs(expected);
    verify(bundleService).export(eq(sourceDir), eq(false));
  }

  @Test
  void exportWithIncludeDraftsCallsExportWithTrue() throws Exception {
    DefinitionBundle expected = emptyBundle();
    when(bundleService.export(eq(sourceDir), eq(true))).thenReturn(expected);

    ServerResponse response = handler.exportBundle(
            getRequest("/api/dsl/definitions/export?include=drafts"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DefinitionBundle actual = entityOf(response);
    assertThat(actual).isSameAs(expected);
    verify(bundleService).export(eq(sourceDir), eq(true));
  }

  @Test
  void exportWithOtherIncludeValueCallsExportWithFalse() throws Exception {
    DefinitionBundle expected = emptyBundle();
    when(bundleService.export(eq(sourceDir), eq(false))).thenReturn(expected);

    ServerResponse response = handler.exportBundle(
            getRequest("/api/dsl/definitions/export?include=other"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    DefinitionBundle actual = entityOf(response);
    assertThat(actual).isSameAs(expected);
    verify(bundleService).export(eq(sourceDir), eq(false));
  }

  @Test
  void importWithoutDryRunDefaultsToFalseAndAppliesBundle() throws Exception {
    when(reloadHandler.reloadDefinitions()).thenReturn(LoadResult.empty());
    when(bundleService.applyToTarget(eq(sourceDir), any(DefinitionBundle.class)))
            .thenReturn(List.of(new ImportEntryResult("A", "published", null)));

    DefinitionBundle bundle = sampleBundle();
    ServerResponse response = handler.importBundle(postBundle(bundle, null));

    assertThat(response.statusCode().value()).isEqualTo(200);
    ImportBundleResult result = entityOf(response);
    assertThat(result.dryRun()).isFalse();
    verify(bundleService).validateForImport(any(DefinitionBundle.class));
    verify(bundleService).verifyDigest(any(DefinitionBundle.class));
    verify(bundleService).applyToTarget(eq(sourceDir), any(DefinitionBundle.class));
    verify(bundleService, never()).diffForImport(any(), any());
    verify(builderClient).flushFiles();
  }

  @Test
  void importWithDryRunTrueForwardsTrueAndPreviewsDiff() throws Exception {
    when(bundleService.diffForImport(eq(sourceDir), any(DefinitionBundle.class)))
            .thenReturn(List.of(new ImportEntryResult("A", "created", null)));

    DefinitionBundle bundle = sampleBundle();
    ServerResponse response = handler.importBundle(postBundle(bundle, true));

    assertThat(response.statusCode().value()).isEqualTo(200);
    ImportBundleResult result = entityOf(response);
    assertThat(result.dryRun()).isTrue();
    verify(bundleService).validateForImport(any(DefinitionBundle.class));
    verify(bundleService).verifyDigest(any(DefinitionBundle.class));
    verify(bundleService).diffForImport(eq(sourceDir), any(DefinitionBundle.class));
    verify(bundleService, never()).applyToTarget(any(), any());
    verify(builderClient, never()).flushFiles();
  }

  @Test
  void importPropagatesIOExceptionFromBodyRead() throws Exception {
    ServerRequest request = mock(ServerRequest.class);
    when(request.param("dryRun")).thenReturn(Optional.empty());
    when(request.body(String.class)).thenThrow(new ServletException("body read failed"));

    assertThatThrownBy(() -> handler.importBundle(request))
            .isInstanceOf(IOException.class)
            .hasMessage("Failed to read request body")
            .hasCauseInstanceOf(ServletException.class)
            .satisfies(e -> assertThat(((ServletException) e.getCause()).getMessage())
                    .isEqualTo("body read failed"));
  }

  private static DefinitionBundle emptyBundle() {
    return new DefinitionBundle(1, "dev", "2026-01-01T00:00:00Z", List.of(), "digest");
  }

  private static DefinitionBundle sampleBundle() {
    return new DefinitionBundle(1, "dev", "2026-01-01T00:00:00Z", List.of(
            new DefinitionBundleEntry(
                    new DraftRequest("A", "process", null, null, null, "class A {}", null),
                    "source")),
            null);
  }

  private ServerRequest getRequest(String path) {
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
    return ServerRequest.create(req, CONVERTERS);
  }

  private ServerRequest postBundle(DefinitionBundle bundle, Boolean dryRun) throws IOException {
    String path = "/api/dsl/definitions/import";
    if (Boolean.TRUE.equals(dryRun)) {
      path = path + "?dryRun=true";
    }
    var req = new MockHttpServletRequest("POST", path);
    if (Boolean.TRUE.equals(dryRun)) {
      req.setQueryString("dryRun=true");
      req.addParameter("dryRun", "true");
    }
    req.setContentType("application/json");
    req.setContent(mapper.writeValueAsString(bundle).getBytes(StandardCharsets.UTF_8));
    return ServerRequest.create(req, CONVERTERS);
  }

  @SuppressWarnings("unchecked")
  private static <T> T entityOf(ServerResponse response) {
    return (T) ((EntityResponse<?>) response).entity();
  }

  private static final List<HttpMessageConverter<?>> CONVERTERS = List.of(
          new ByteArrayHttpMessageConverter(),
          new StringHttpMessageConverter());
}
