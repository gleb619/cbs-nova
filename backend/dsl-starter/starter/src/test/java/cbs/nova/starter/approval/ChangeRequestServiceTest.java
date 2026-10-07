package cbs.nova.starter.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cbs.nova.starter.AuditTestSupport;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.DslDraftHandler;
import cbs.nova.starter.entity.ChangeRequestEntity;
import cbs.nova.starter.entity.ChangeRequestEntity.Status;
import cbs.nova.starter.exception.ChangeRequestException;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import cbs.nova.starter.persistence.ChangeRequestRepository;
import cbs.nova.starter.security.Role;
import cbs.nova.starter.service.ChangeRequestService;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.service.DslSourcePathResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link ChangeRequestService} (T568): approver validation (AUTHOR rank, no
 * self-approval below ADMIN), compare-and-set approve/reject, publish failure recovery, and
 * concurrent-decision races.
 */
class ChangeRequestServiceTest {

  private static final String SNAPSHOT = """
          {"name":"LoanA","type":"process","status":"Draft","version":"1"}
          """;

  @TempDir
  Path sourceDir;

  private final ObjectMapper mapper = new ObjectMapper();
  private ChangeRequestRepository repository;
  private DslDraftHandler draftHandler;
  private DslAuditService auditService;
  private ChangeRequestService service;

  @BeforeEach
  void setUp() {
    repository = mock(ChangeRequestRepository.class);
    draftHandler = mock(DslDraftHandler.class);
    auditService = mock(DslAuditService.class);
    DslProperties props = DslProperties.builder().sourceDir(sourceDir.toString()).build();
    DslSourcePathResolver sourcePathResolver = new DslSourcePathResolver(props,
            name -> name.equals("LoanA") ? Optional.of("dsl/LoanADsl.java") : Optional.empty());
    service = new ChangeRequestService(repository, props, mapper,
            draftHandlerProvider(draftHandler), providerOfBean(sourcePathResolver),
            providerOfBean(auditService));
  }

  @Test
  void approveByDifferentAuthorPublishesSnapshot() throws Exception {
    when(repository.findById(7L)).thenReturn(Optional.of(pending(7L, "alice")));
    when(repository.transition(eq(7L), eq(Status.PENDING), eq(Status.APPROVED), eq("bob"),
            any(Instant.class), eq("ok"))).thenReturn(true);
    when(draftHandler.publishPayload(any(), any(), any()))
            .thenReturn(ServerResponse.ok().build());

    ServerResponse response = service.approve(7L, "bob", Role.AUTHOR, "ok", null);

    assertThat(response.statusCode().value()).isEqualTo(200);
    verify(repository, never()).updateStatus(anyLong(), any(), any(), any(), any());
    verify(draftHandler).publishPayload(isNull(), eq("LoanA"),
            argThat(argThatDraft(draft -> "Published".equals(draft.status())
                    && "LoanA".equals(draft.name())
                    && "1".equals(draft.version()))));
    verify(auditService).record(eq("bob"),
            eq("CHANGE_REQUEST_APPROVE"), eq("change-request:7"), isNull(),
            eq("SUCCESS"), argThat(details -> details instanceof Map<?, ?> map
                    && "LoanA".equals(map.get("definitionName"))));
  }

  @Test
  void adminMayApproveOwnRequest() throws Exception {
    when(repository.findById(7L)).thenReturn(Optional.of(pending(7L, "alice")));
    when(repository.transition(eq(7L), eq(Status.PENDING), eq(Status.APPROVED), eq("alice"),
            any(Instant.class), isNull())).thenReturn(true);
    when(draftHandler.publishPayload(any(), any(), any()))
            .thenReturn(ServerResponse.ok().build());

    service.approve(7L, "alice", Role.ADMIN, null, null);

    verify(draftHandler).publishPayload(any(), eq("LoanA"), any());
  }

  @Test
  void selfApprovalIsBlockedForNonAdmin() {
    when(repository.findById(7L)).thenReturn(Optional.of(pending(7L, "alice")));

    assertThatThrownBy(() -> service.approve(7L, "alice", Role.AUTHOR, null, null))
            .isInstanceOfSatisfying(ChangeRequestException.class,
                    e -> assertThat(e.code()).isEqualTo("FORBIDDEN"));

    verify(repository, never()).transition(anyLong(), any(), any(), any(), any(), any());
    verify(repository, never()).updateStatus(anyLong(), any(), any(), any(), any());
    verifyNoInteractions(draftHandler);
  }

  @Test
  void selfRejectionIsBlockedForNonAdmin() {
    when(repository.findById(7L)).thenReturn(Optional.of(pending(7L, "alice")));

    assertThatThrownBy(() -> service.reject(7L, "alice", Role.AUTHOR, "no", null))
            .isInstanceOfSatisfying(ChangeRequestException.class,
                    e -> assertThat(e.code()).isEqualTo("FORBIDDEN"));

    verify(repository, never()).transition(anyLong(), any(), any(), any(), any(), any());
  }

  @Test
  void viewerRankCannotDecide() {
    when(repository.findById(7L)).thenReturn(Optional.of(pending(7L, "alice")));

    assertThatThrownBy(() -> service.reject(7L, "bob", Role.VIEWER, "no", null))
            .isInstanceOfSatisfying(ChangeRequestException.class,
                    e -> assertThat(e.code()).isEqualTo("FORBIDDEN"));

    verify(repository, never()).transition(anyLong(), any(), any(), any(), any(), any());
  }

  @Test
  void nonPendingRequestCannotBeDecided() {
    ChangeRequestEntity approved = new ChangeRequestEntity(7L, "LoanA", SNAPSHOT, "alice",
            Instant.now(), Status.APPROVED, "carol", Instant.now(), "ok");
    when(repository.findById(7L)).thenReturn(Optional.of(approved));

    assertThatThrownBy(() -> service.approve(7L, "bob", Role.AUTHOR, null, null))
            .isInstanceOfSatisfying(ChangeRequestException.class,
                    e -> assertThat(e.code()).isEqualTo("CONFLICT"));

    verify(repository, never()).transition(anyLong(), any(), any(), any(), any(), any());
    verifyNoInteractions(draftHandler);
  }

  @Test
  void missingRequestCannotBeDecided() {
    when(repository.findById(7L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.reject(7L, "bob", Role.AUTHOR, "no", null))
            .isInstanceOfSatisfying(ChangeRequestException.class,
                    e -> assertThat(e.code()).isEqualTo("NOT_FOUND"));
  }

  @Test
  void rejectRecordsCommentAndNeverPublishes() {
    when(repository.findById(7L)).thenReturn(Optional.of(pending(7L, "alice")));
    when(repository.transition(eq(7L), eq(Status.PENDING), eq(Status.REJECTED), isNull(),
            isNull(), eq("not now"))).thenReturn(true);

    ChangeRequestEntity rejected = service.reject(7L, "bob", Role.AUTHOR, "not now", null);

    verify(repository, never()).updateStatus(anyLong(), any(), any(), any(), any());
    verifyNoInteractions(draftHandler);
    assertThat(rejected.status()).isEqualTo(Status.REJECTED);
    assertThat(rejected.comment()).isEqualTo("not now");
    assertThat(rejected.approvedBy()).isNull();
  }

  @Test
  void draftHandlerAbsentBeforeClaimDoesNotChangeState() {
    when(repository.findById(7L)).thenReturn(Optional.of(pending(7L, "alice")));
    service = new ChangeRequestService(repository,
            DslProperties.builder().sourceDir(sourceDir.toString()).build(), mapper,
            emptyDraftHandlerProvider(), providerOfBean(mock(DslSourcePathResolver.class)),
            providerOfBean(auditService));

    assertThatThrownBy(() -> service.approve(7L, "bob", Role.AUTHOR, null, null))
            .isInstanceOfSatisfying(ChangeRequestException.class,
                    e -> assertThat(e.code()).isEqualTo("CONFLICT"));

    verify(repository, never()).transition(anyLong(), any(), any(), any(), any(), any());
    verify(repository, never()).updateStatus(anyLong(), any(), any(), any(), any());
  }

  @Test
  void publishErrorRevertsToPendingAndAuditsFailure() throws Exception {
    when(repository.findById(7L)).thenReturn(Optional.of(pending(7L, "alice")));
    when(repository.transition(eq(7L), eq(Status.PENDING), eq(Status.APPROVED), eq("bob"),
            any(Instant.class), isNull())).thenReturn(true);
    when(repository.transition(eq(7L), eq(Status.APPROVED), eq(Status.PENDING), isNull(),
            isNull(), isNull())).thenReturn(true);
    when(draftHandler.publishPayload(any(), any(), any()))
            .thenReturn(ServerResponse.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "bad")));

    assertThatThrownBy(() -> service.approve(7L, "bob", Role.AUTHOR, null, null))
            .isInstanceOfSatisfying(ChangeRequestException.class,
                    e -> assertThat(e.code()).isEqualTo("CONFLICT"));

    InOrder order = inOrder(repository);
    order.verify(repository).transition(eq(7L), eq(Status.PENDING), eq(Status.APPROVED), eq("bob"),
            any(Instant.class), isNull());
    order.verify(repository).transition(eq(7L), eq(Status.APPROVED), eq(Status.PENDING), isNull(),
            isNull(), isNull());
    verify(auditService).record(eq("bob"), eq("CHANGE_REQUEST_APPROVE"),
            eq("change-request:7"), isNull(), eq("FAILURE"),
            argThat(details -> details instanceof Map<?, ?> map
                    && "LoanA".equals(map.get("definitionName"))
                    && ((String) map.get("error")).contains("HTTP 400")));
  }

  @Test
  void publishExceptionRevertsToPendingAndRethrows() throws Exception {
    when(repository.findById(7L)).thenReturn(Optional.of(pending(7L, "alice")));
    when(repository.transition(eq(7L), eq(Status.PENDING), eq(Status.APPROVED), eq("bob"),
            any(Instant.class), isNull())).thenReturn(true);
    when(repository.transition(eq(7L), eq(Status.APPROVED), eq(Status.PENDING), isNull(),
            isNull(), isNull())).thenReturn(true);
    when(draftHandler.publishPayload(any(), any(), any()))
            .thenThrow(new IOException("builder down"));

    assertThatThrownBy(() -> service.approve(7L, "bob", Role.AUTHOR, null, null))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("builder down");

    verify(repository).transition(eq(7L), eq(Status.APPROVED), eq(Status.PENDING), isNull(),
            isNull(), isNull());
    verify(auditService).record(eq("bob"), eq("CHANGE_REQUEST_APPROVE"),
            eq("change-request:7"), isNull(), eq("FAILURE"),
            argThat(details -> details instanceof Map<?, ?> map
                    && "builder down".equals(map.get("error"))));
  }

  @Test
  void twoConcurrentApprovalsPublishOnce() throws Exception {
    when(repository.findById(7L)).thenReturn(Optional.of(pending(7L, "alice")));
    when(repository.transition(eq(7L), eq(Status.PENDING), eq(Status.APPROVED), eq("bob"),
            any(Instant.class), isNull())).thenReturn(true, false);
    when(draftHandler.publishPayload(any(), any(), any()))
            .thenReturn(ServerResponse.ok().build());

    ServerResponse first = service.approve(7L, "bob", Role.AUTHOR, null, null);
    assertThat(first.statusCode().value()).isEqualTo(200);

    assertThatThrownBy(() -> service.approve(7L, "bob", Role.AUTHOR, null, null))
            .isInstanceOfSatisfying(ChangeRequestException.class,
                    e -> assertThat(e.code()).isEqualTo("CONFLICT"));

    verify(draftHandler, Mockito.times(1)).publishPayload(any(), any(), any());
  }

  @Test
  void approveVersusRejectRaceProducesSingleWinner() throws Exception {
    when(repository.findById(7L)).thenReturn(Optional.of(pending(7L, "alice")));
    when(repository.transition(eq(7L), eq(Status.PENDING), eq(Status.APPROVED), eq("bob"),
            any(Instant.class), isNull())).thenReturn(true);
    when(repository.transition(eq(7L), eq(Status.PENDING), eq(Status.REJECTED), isNull(),
            isNull(), eq("no"))).thenReturn(false);
    when(draftHandler.publishPayload(any(), any(), any()))
            .thenReturn(ServerResponse.ok().build());

    ServerResponse approved = service.approve(7L, "bob", Role.AUTHOR, null, null);
    assertThat(approved.statusCode().value()).isEqualTo(200);

    assertThatThrownBy(() -> service.reject(7L, "bob", Role.AUTHOR, "no", null))
            .isInstanceOfSatisfying(ChangeRequestException.class,
                    e -> assertThat(e.code()).isEqualTo("CONFLICT"));

    verify(draftHandler, Mockito.times(1)).publishPayload(any(), any(), any());
  }

  @Test
  void secondSubmitSupersedesPreviousPendingRequest() throws IOException {
    writeDraft("LoanA");
    when(repository.findPendingByDefinitionName("LoanA"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(new ChangeRequestEntity(3L, "LoanA", "old", "alice",
                    Instant.now(), Status.PENDING, null, null, null)));
    when(repository.insert(any())).thenReturn(1L, 2L);

    service.submit("LoanA", "alice", null);
    service.submit("LoanA", "bob", null);

    verify(repository).updateStatus(eq(3L), eq(Status.SUPERSEDED), isNull(), isNull(), isNull());
    verify(repository, Mockito.times(2)).insert(any());
  }

  @Test
  void submitWithoutDraftFails() {
    assertThatThrownBy(() -> service.submit("LoanA", "alice", null))
            .isInstanceOfSatisfying(ChangeRequestException.class,
                    e -> assertThat(e.code()).isEqualTo("NOT_FOUND"));

    verify(repository, never()).insert(any());
  }

  private void writeDraft(String name) throws IOException {
    Path dsl = sourceDir.resolve("dsl");
    Files.createDirectories(dsl);
    Files.writeString(dsl.resolve(name + "Dsl.java"), "// draft for " + name,
            StandardCharsets.UTF_8);
  }

  private static ChangeRequestEntity pending(long id, String requestedBy) {
    return new ChangeRequestEntity(id, "LoanA", SNAPSHOT, requestedBy, Instant.now(),
            Status.PENDING, null, null, null);
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<DslDraftHandler> draftHandlerProvider(DslDraftHandler handler) {
    ObjectProvider<DslDraftHandler> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(handler);
    return provider;
  }

  private static ObjectProvider<DslDraftHandler> emptyDraftHandlerProvider() {
    @SuppressWarnings("unchecked")
    ObjectProvider<DslDraftHandler> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(null);
    return provider;
  }

  private static <T> ObjectProvider<T> providerOfBean(T bean) {
    @SuppressWarnings("unchecked")
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(bean);
    return provider;
  }

  private static ArgumentMatcher<DraftRequest> argThatDraft(
          Predicate<DraftRequest> predicate) {
    return new ArgumentMatcher<>() {
      @Override
      public boolean matches(DraftRequest draft) {
        return draft != null && predicate.test(draft);
      }
    };
  }
}
