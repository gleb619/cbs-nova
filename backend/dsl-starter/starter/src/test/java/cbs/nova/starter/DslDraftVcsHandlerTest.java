package cbs.nova.starter;

import static cbs.nova.starter.BuilderClientTestSupport.providerOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.DslDraftVcsHandler;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.exception.BuilderApiException;
import cbs.nova.starter.model.VcsModels.CommitRequest;
import cbs.nova.starter.model.VcsModels.CommitResult;
import cbs.nova.starter.model.VcsModels.FetchResult;
import cbs.nova.starter.model.VcsModels.PullResult;
import cbs.nova.starter.model.VcsModels.RebaseResult;
import cbs.nova.starter.service.DomainEventPublisher;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.service.DslSourcePathResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.EntityResponse;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

class DslDraftVcsHandlerTest {

  private DslDraftVcsHandler handler;
  private DslBuilderClient client;
  private DslSourcePathResolver sourcePathResolver;
  private DslAuditService auditService;
  private DomainEventPublisher eventPublisher;
  private DslProperties props;
  private Path sourceDir;
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() throws IOException {
    sourceDir = Files.createTempDirectory("dsl-draft-vcs-test-");
    props = DslProperties.builder().sourceDir(sourceDir.toString()).build();
    client = mock(DslBuilderClient.class);
    sourcePathResolver = mock(DslSourcePathResolver.class);
    auditService = mock(DslAuditService.class);
    eventPublisher = mock(DomainEventPublisher.class);
    handler = new DslDraftVcsHandler(props, mapper,
            providerOf(client), providerOfBean(auditService), providerOfBean(eventPublisher),
            providerOfBean(sourcePathResolver));
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
  void fetchDelegatesToBuilder() {
    when(client.vcsFetch()).thenReturn(new FetchResult("main", true, "abc", 0, 1));

    ServerResponse response = handler.fetch(postRequest("/api/dsl/drafts/fetch", null));

    assertThat(response.statusCode().value()).isEqualTo(200);
    FetchResult body = (FetchResult) ((EntityResponse<?>) response).entity();
    assertThat(body.behind()).isEqualTo(1);
    verify(client).vcsFetch();
    verify(auditService).record(anyString(), eq("DRAFT_VCS_FETCH"), eq("repository"), any(),
            eq(StarterConstants.OUTCOME_SUCCESS), any());
  }

  @Test
  void pullDelegatesToBuilder() {
    when(client.vcsPull()).thenReturn(
            new PullResult("main", "REBASE", true, "a", "b", 1, 0, null));

    ServerResponse response = handler.pull(postRequest("/api/dsl/drafts/pull", null));

    assertThat(response.statusCode().value()).isEqualTo(200);
    PullResult body = (PullResult) ((EntityResponse<?>) response).entity();
    assertThat(body.pulled()).isTrue();
    verify(eventPublisher).publish(any());
  }

  @Test
  void rebaseDelegatesToBuilder() {
    when(client.vcsRebase()).thenReturn(new RebaseResult("main", true, "a", "b", 1, 0, null));

    ServerResponse response = handler.rebase(postRequest("/api/dsl/drafts/rebase", null));

    assertThat(response.statusCode().value()).isEqualTo(200);
    RebaseResult body = (RebaseResult) ((EntityResponse<?>) response).entity();
    assertThat(body.rebased()).isTrue();
  }

  @Test
  void repoOpsReturn409WhenBuilderUnavailable() {
    var handlerWithoutBuilder = new DslDraftVcsHandler(props, mapper,
            providerOf(null), providerOfBean(auditService), providerOfBean(eventPublisher),
            providerOfBean(sourcePathResolver));

    assertThat(handlerWithoutBuilder.fetch(postRequest("/api/dsl/drafts/fetch", null))
            .statusCode().value()).isEqualTo(409);
    assertThat(handlerWithoutBuilder.pull(postRequest("/api/dsl/drafts/pull", null))
            .statusCode().value()).isEqualTo(409);
    assertThat(handlerWithoutBuilder.rebase(postRequest("/api/dsl/drafts/rebase", null))
            .statusCode().value()).isEqualTo(409);
  }

  @Test
  void repoOpsReturn409WhenSourceDirNotConfigured() throws IOException {
    var bareProps = DslProperties.builder().build();
    var unconfigured = new DslDraftVcsHandler(bareProps, mapper,
            providerOf(client), providerOfBean(auditService), providerOfBean(eventPublisher),
            providerOfBean(sourcePathResolver));

    assertThat(unconfigured.fetch(postRequest("/api/dsl/drafts/fetch", null))
            .statusCode().value()).isEqualTo(409);
    assertThat(unconfigured.pull(postRequest("/api/dsl/drafts/pull", null))
            .statusCode().value()).isEqualTo(409);
    assertThat(unconfigured.rebase(postRequest("/api/dsl/drafts/rebase", null))
            .statusCode().value()).isEqualTo(409);
    verifyNoInteractions(client);
  }

  @Test
  void commitFlushesPendingWritesThenCommitsAllPathsAsOneCommit() throws Exception {
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));
    when(sourcePathResolver.relativePath("bar")).thenReturn(Optional.of("dsl/BarDsl.java"));
    when(client.commit(any())).thenReturn(
            new CommitResult("abc123", List.of("dsl/FooDsl.java", "dsl/BarDsl.java"), 42, null,
                    null));

    ServerResponse response = handler.commit(postRequest("/api/dsl/drafts/commit",
            "{\"message\":\"ship it\",\"names\":[\"foo\",\"bar\"]}"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    CommitResult body = (CommitResult) ((EntityResponse<?>) response).entity();
    assertThat(body.commitId()).isEqualTo("abc123");
    InOrder order = inOrder(client);
    order.verify(client).flushFiles();
    order.verify(client).commit(any());
    verify(client).commit(argThatCommit(List.of("dsl/FooDsl.java", "dsl/BarDsl.java"),
            "ship it"));
    verify(auditService).record(anyString(), eq("DRAFTS_COMMIT"), eq("repository"), any(),
            eq(StarterConstants.OUTCOME_SUCCESS), any());
    verify(eventPublisher).publish(any());
  }

  @Test
  void commitReturns404AndCommitsNothingWhenANameHasNoSourcePath() throws Exception {
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));
    when(sourcePathResolver.relativePath("ghost")).thenReturn(Optional.empty());

    ServerResponse response = handler.commit(postRequest("/api/dsl/drafts/commit",
            "{\"message\":\"ship it\",\"names\":[\"foo\",\"ghost\"]}"));

    // I2 atomicity: nothing is committed when any listed name cannot be resolved.
    assertThat(response.statusCode().value()).isEqualTo(404);
    verify(client, never()).commit(any());
  }

  @Test
  void commitReturns400WhenNamesEmpty() throws Exception {
    ServerResponse response = handler.commit(
            postRequest("/api/dsl/drafts/commit", "{\"message\":\"x\",\"names\":[]}"));

    assertThat(response.statusCode().value()).isEqualTo(400);
    verify(client, never()).commit(any());
  }

  @Test
  void commitReturns400WhenMessageBlank() throws Exception {
    ServerResponse response = handler.commit(
            postRequest("/api/dsl/drafts/commit", "{\"message\":\" \",\"names\":[\"foo\"]}"));

    assertThat(response.statusCode().value()).isEqualTo(400);
    verify(client, never()).commit(any());
  }

  @Test
  void commitRethrowsBuilderConflictAndAuditsFailure() {
    when(sourcePathResolver.relativePath("foo")).thenReturn(Optional.of("dsl/FooDsl.java"));
    when(client.commit(any())).thenThrow(new BuilderApiException(
            HttpStatus.CONFLICT, "NOTHING_TO_COMMIT", "no git change for path(s)"));

    assertThatThrownBy(() -> handler.commit(postRequest("/api/dsl/drafts/commit",
            "{\"message\":\"x\",\"names\":[\"foo\"]}")))
            .isInstanceOfSatisfying(BuilderApiException.class,
                    ex -> assertThat(ex.getCode()).isEqualTo("NOTHING_TO_COMMIT"));
    verify(auditService).record(anyString(), eq("DRAFTS_COMMIT"), eq("repository"), any(),
            eq(StarterConstants.OUTCOME_FAILURE), any());
  }

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> providerOfBean(T bean) {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(bean);
    return provider;
  }

  private static CommitRequest argThatCommit(List<String> paths, String message) {
    return org.mockito.ArgumentMatchers.argThat(r -> r.paths().equals(paths)
            && r.message().equals(message));
  }

  private static ServerRequest postRequest(String path, String json) {
    var req = new MockHttpServletRequest("POST", path);
    req.setContentType("application/json");
    req.setContent((json == null ? "{}" : json).getBytes(StandardCharsets.UTF_8));
    return ServerRequest.create(req, CONVERTERS);
  }

  private static final List<HttpMessageConverter<?>> CONVERTERS = List
          .of(new ByteArrayHttpMessageConverter(),
                  new StringHttpMessageConverter());
}
