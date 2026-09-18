package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.starter.AuditTestSupport;
import cbs.nova.starter.config.properties.CbsDslManifestProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.model.InvariantContext;
import cbs.nova.starter.model.Piece;
import cbs.nova.starter.model.PostCheck;
import cbs.nova.starter.model.Target;
import cbs.nova.starter.service.check.AuditWriteHook;
import cbs.nova.starter.service.check.InvariantAssertHook;
import cbs.nova.starter.service.check.NotifyHook;
import cbs.nova.starter.service.check.PostCheckHook;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Behaviour matrix for {@link PieceCheckPipeline}: off-request-thread execution, per-hook-type
 * happy paths, and the {@code onFailure} warn vs block-next-execution policies.
 */
class PieceCheckPipelineTest {

  private static final String PIECE_ID = "dsl-reload";
  private static final String PRINCIPAL = "auth:alice";
  private static final String CORRELATION_ID = "rid-123";

  private DslAuditService auditService;
  private ObjectProvider<DslAuditService> auditProvider;
  private DomainEventPublisher eventPublisher;
  private ObjectProvider<DomainEventPublisher> eventPublisherProvider;
  private PieceCheckBlockRegistry blockRegistry;
  private MeterRegistry meters;

  @BeforeEach
  void setUp() {
    auditService = mock(DslAuditService.class);
    auditProvider = AuditTestSupport.providerOf(auditService);
    eventPublisher = mock(DomainEventPublisher.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<DomainEventPublisher> provider = mock(ObjectProvider.class);
    eventPublisherProvider = provider;
    when(eventPublisherProvider.getIfAvailable()).thenReturn(eventPublisher);
    blockRegistry = new PieceCheckBlockRegistry(defaultProperties(), System::currentTimeMillis);
    meters = new SimpleMeterRegistry();
  }

  // --- execution model ---------------------------------------------------------

  @Test
  void hooksRunOffTheRequestThreadAndNeverDelayTheCaller() throws Exception {
    CountDownLatch hookRan = new CountDownLatch(1);
    AtomicReference<String> hookThread = new AtomicReference<>();
    PostCheckHook capturingHook = new PostCheckHook() {
      @Override
      public String type() {
        return "notify";
      }

      @Override
      public void run(PostCheck check, Invocation invocation) {
        hookThread.set(Thread.currentThread().getName());
        hookRan.countDown();
      }
    };
    // Executor parks the task instead of running it: onSuccess must return before the hook runs.
    AtomicReference<Runnable> parked = new AtomicReference<>();
    Executor parkingExecutor = parked::set;
    PieceCheckPipeline pipeline = pipeline(parkingExecutor, List.of(capturingHook));

    long start = System.nanoTime();
    pipeline.onSuccess(piece(new PostCheck.NotifyCheck("workbench")), PRINCIPAL, "alice",
            CORRELATION_ID, context(200));
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

    assertThat(elapsedMs).isLessThan(1_000);
    assertThat(hookThread.get()).as("hook must not run synchronously on the caller thread")
            .isNull();

    // Running the parked task on a dedicated thread lands the hook on that thread, not ours.
    Thread worker = new Thread(parked.get(), "cbs-piece-check-1");
    worker.start();
    assertThat(hookRan.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(hookThread.get()).isEqualTo("cbs-piece-check-1");
  }

  @Test
  void pieceWithoutPostChecksNeverTouchesTheExecutor() {
    List<Runnable> submitted = new ArrayList<>();
    PieceCheckPipeline pipeline = pipeline(submitted::add, hooks());

    pipeline.onSuccess(piece(), PRINCIPAL, "alice", CORRELATION_ID, context(200));

    assertThat(submitted).isEmpty();
  }

  // --- audit-write ---------------------------------------------------------------

  @Test
  void auditWriteHappyPathRecordsSuccessRowWithCheckAction() {
    PieceCheckPipeline pipeline = pipeline(Runnable::run, hooks());

    pipeline.onSuccess(piece(new PostCheck.AuditWriteCheck("DEFINITION_RELOAD")), PRINCIPAL,
            "alice", CORRELATION_ID, context(200));

    verify(auditService).record(eq("alice"), eq("DEFINITION_RELOAD"), eq(PIECE_ID),
            eq(CORRELATION_ID), eq(StarterConstants.OUTCOME_SUCCESS), any());
    assertThat(
            meters.counter(StarterConstants.POSTCHECK_TOTAL_COUNTER, "hook", "audit-write").count())
            .isEqualTo(1.0);
    assertThat(meters.counter(StarterConstants.POSTCHECK_FAILED_COUNTER, "hook", "audit-write")
            .count()).isZero();
  }

  @Test
  void auditWriteIsNoOpWithoutAuditServiceBean() {
    PieceCheckPipeline pipeline = new PieceCheckPipeline(Runnable::run,
            List.of(new AuditWriteHook(AuditTestSupport.emptyProvider()),
                    new InvariantAssertHook(), new NotifyHook(eventPublisherProvider)),
            blockRegistry, AuditTestSupport.emptyProvider(), meters);

    pipeline.onSuccess(piece(new PostCheck.AuditWriteCheck("DEFINITION_RELOAD")), PRINCIPAL,
            null, null, context(200));

    verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
    assertThat(blockRegistry.isBlocked(PIECE_ID, PRINCIPAL)).isFalse();
  }

  @Test
  void auditWriteFailureUnderWarnPolicyIsAuditedFailureAndNeverBlocks() {
    doThrow(new RuntimeException("audit store down")).when(auditService)
            .record(eq("alice"), eq("DEFINITION_RELOAD"), eq(PIECE_ID), eq(CORRELATION_ID),
                    eq(StarterConstants.OUTCOME_SUCCESS), any());
    PieceCheckPipeline pipeline = pipeline(Runnable::run, hooks());

    pipeline.onSuccess(piece(new PostCheck.AuditWriteCheck("DEFINITION_RELOAD")), PRINCIPAL,
            "alice", CORRELATION_ID, context(200));

    // Failure policy warn: FAILURE audit row, failed counter, no block, no propagation.
    verify(auditService).record(eq("alice"),
            eq(StarterConstants.ACTION_PIECE_POSTCHECK_FAILURE), eq(PIECE_ID), eq(CORRELATION_ID),
            eq(StarterConstants.OUTCOME_FAILURE), any());
    assertThat(blockRegistry.isBlocked(PIECE_ID, PRINCIPAL)).isFalse();
    assertThat(meters.counter(StarterConstants.POSTCHECK_FAILED_COUNTER, "hook", "audit-write")
            .count()).isEqualTo(1.0);
  }

  // --- invariant-assert ------------------------------------------------------------

  @Test
  void invariantAssertHappyPathPassesMatchingCondition() {
    PieceCheckPipeline pipeline = pipeline(Runnable::run, hooks());

    pipeline.onSuccess(
            piece(new PostCheck.InvariantAssertCheck("status-2xx", "reload returned ok", null)),
            PRINCIPAL, "alice", CORRELATION_ID, context(200));

    verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
    assertThat(meters.counter(StarterConstants.POSTCHECK_TOTAL_COUNTER, "hook", "invariant-assert")
            .count()).isEqualTo(1.0);
    assertThat(meters.counter(StarterConstants.POSTCHECK_FAILED_COUNTER, "hook", "invariant-assert")
            .count()).isZero();
  }

  @Test
  void invariantAssertViolationUnderWarnPolicyDoesNotBlock() {
    PieceCheckPipeline pipeline = pipeline(Runnable::run, hooks());

    pipeline.onSuccess(
            piece(new PostCheck.InvariantAssertCheck("status-2xx", "must be a direct hit", null)),
            PRINCIPAL, "alice", CORRELATION_ID, context(302));

    assertThat(blockRegistry.isBlocked(PIECE_ID, PRINCIPAL)).isFalse();
    verify(auditService).record(anyString(),
            eq(StarterConstants.ACTION_PIECE_POSTCHECK_FAILURE), eq(PIECE_ID), eq(CORRELATION_ID),
            eq(StarterConstants.OUTCOME_FAILURE), any());
    assertThat(meters.counter(StarterConstants.POSTCHECK_FAILED_COUNTER, "hook", "invariant-assert")
            .count()).isEqualTo(1.0);
  }

  @Test
  void invariantAssertViolationUnderBlockPolicyBlocksNextExecution() {
    PieceCheckPipeline pipeline = pipeline(Runnable::run, hooks());

    pipeline.onSuccess(
            piece(new PostCheck.InvariantAssertCheck("status-2xx", "must be a direct hit",
                    PostCheck.ON_FAILURE_BLOCK)),
            PRINCIPAL, "alice", CORRELATION_ID, context(302));

    assertThat(blockRegistry.isBlocked(PIECE_ID, PRINCIPAL)).isTrue();
    assertThat(meters.counter(StarterConstants.POSTCHECK_FAILED_COUNTER, "hook", "invariant-assert")
            .count()).isEqualTo(1.0);
  }

  @Test
  void unknownInvariantExprFailsTheHookInsteadOfPassing() {
    PieceCheckPipeline pipeline = pipeline(Runnable::run, hooks());

    pipeline.onSuccess(piece(new PostCheck.InvariantAssertCheck("status == 200", null, null)),
            PRINCIPAL, "alice", CORRELATION_ID, context(200));

    assertThat(meters.counter(StarterConstants.POSTCHECK_FAILED_COUNTER, "hook", "invariant-assert")
            .count()).isEqualTo(1.0);
  }

  // --- notify --------------------------------------------------------------------

  @Test
  void notifyHappyPathPublishesPieceNotifiedEvent() {
    PieceCheckPipeline pipeline = pipeline(Runnable::run, hooks());

    pipeline.onSuccess(piece(new PostCheck.NotifyCheck("workbench")), PRINCIPAL, "alice",
            CORRELATION_ID, context(200));

    ArgumentCaptor<DomainEvent> event = ArgumentCaptor.forClass(DomainEvent.class);
    verify(eventPublisher).publish(event.capture());
    assertThat(event.getValue()).isInstanceOfSatisfying(DomainEvent.PieceNotified.class,
            notified -> {
              assertThat(notified.pieceId()).isEqualTo(PIECE_ID);
              assertThat(notified.channel()).isEqualTo("workbench");
              assertThat(notified.correlationId()).isEqualTo(CORRELATION_ID);
            });
  }

  @Test
  void notifyPublishFailureUnderBlockPolicyBlocksNextExecution() {
    doThrow(new RuntimeException("dsl_events insert failed")).when(eventPublisher)
            .publish(any(DomainEvent.class));
    PieceCheckPipeline pipeline = pipeline(Runnable::run, hooks());

    pipeline.onSuccess(piece(new PostCheck.NotifyCheck("workbench", PostCheck.ON_FAILURE_BLOCK)),
            PRINCIPAL, "alice", CORRELATION_ID, context(200));

    assertThat(blockRegistry.isBlocked(PIECE_ID, PRINCIPAL)).isTrue();
    assertThat(meters.counter(StarterConstants.POSTCHECK_FAILED_COUNTER, "hook", "notify").count())
            .isEqualTo(1.0);
  }

  @Test
  void notifyWithoutPublisherIsLogOnlyAndStillSucceeds() {
    when(eventPublisherProvider.getIfAvailable()).thenReturn(null);
    PieceCheckPipeline pipeline = pipeline(Runnable::run, hooks());

    pipeline.onSuccess(piece(new PostCheck.NotifyCheck("workbench")), PRINCIPAL, "alice",
            CORRELATION_ID, context(200));

    verify(eventPublisher, never()).publish(any());
    assertThat(meters.counter(StarterConstants.POSTCHECK_FAILED_COUNTER, "hook", "notify").count())
            .isZero();
  }

  // --- helpers ---------------------------------------------------------------------

  private List<PostCheckHook> hooks() {
    return List.of(new AuditWriteHook(auditProvider), new InvariantAssertHook(),
            new NotifyHook(eventPublisherProvider));
  }

  private PieceCheckPipeline pipeline(Executor executor, List<PostCheckHook> hooks) {
    return new PieceCheckPipeline(executor, hooks, blockRegistry, auditProvider, meters);
  }

  private static Piece piece(PostCheck... postChecks) {
    return new Piece(PIECE_ID, new Target.ApiTarget("POST /api/dsl/reload"), List.of(),
            List.of(postChecks), "deny");
  }

  private static InvariantContext context(int status) {
    return new InvariantContext(PIECE_ID, "AUTHOR", "POST", "/api/dsl/reload", status);
  }

  private static CbsDslManifestProperties defaultProperties() {
    return new CbsDslManifestProperties(true, "classpath:piece-manifest.yaml", null, null,
            new CbsDslManifestProperties.PostCheck(2, Duration.ofMinutes(30), "principal"));
  }
}
