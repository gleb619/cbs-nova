package cbs.nova.starter.service;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.InvariantContext;
import cbs.nova.starter.model.Piece;
import cbs.nova.starter.model.PostCheck;
import cbs.nova.starter.service.check.PostCheckHook;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Post-check hook pipeline (T550): after a manifest-guarded piece executes <b>successfully</b>,
 * runs its {@code postCheck[]} hooks asynchronously on a dedicated executor — never the request
 * thread, never blocking the response. Hooks never change the outcome of the original execution:
 * the response is already on the wire by the time they run.
 *
 * <p>
 * Callers: {@code PieceGuardFilter} (api targets, T549) and — later — the button/object guards
 * (T551/T552); the pipeline is a plain bean any caller can invoke, but only ever on the success
 * path.
 *
 * <p>
 * Per-hook failure policy ({@code onFailure}, T547 schema):
 * <ul>
 * <li>{@code warn} (default) — log ERROR with the correlation id, audit a FAILURE row, done.</li>
 * <li>{@code block-next-execution} — as {@code warn}, plus raise a TTL'd block in
 * {@link PieceCheckBlockRegistry}; the guard denies subsequent executions of the piece while the
 * block is active. Rollback/compensation of the executed piece is explicitly out of scope — most
 * executions (Temporal workflow starts, published definitions) are not transactionally reversible;
 * {@code block-next-execution} is the strongest available corrective and is deliberately
 * manual-review-shaped.</li>
 * </ul>
 *
 * <p>
 * Observability: Micrometer counters {@code dsl.piece.postcheck.total} /
 * {@code dsl.piece.postcheck.failed} tagged by hook type; hook threads carry the request's
 * correlation id in the MDC {@code rid} key (T384 pattern).
 */
@Slf4j
public class PieceCheckPipeline {

  private final Executor executor;
  private final Map<String, PostCheckHook> hooksByType;
  private final PieceCheckBlockRegistry blockRegistry;
  private final @Nullable ObjectProvider<DslAuditService> auditServiceProvider;
  private final @Nullable MeterRegistry meterRegistry;

  public PieceCheckPipeline(
          Executor executor,
          List<PostCheckHook> hooks,
          PieceCheckBlockRegistry blockRegistry,
          @Nullable ObjectProvider<DslAuditService> auditServiceProvider,
          @Nullable MeterRegistry meterRegistry) {
    this.executor = executor;
    this.hooksByType = hooks.stream()
            .collect(Collectors.toUnmodifiableMap(
                    hook -> hook.type().toLowerCase(Locale.ROOT), Function.identity()));
    this.blockRegistry = blockRegistry;
    this.auditServiceProvider = auditServiceProvider;
    this.meterRegistry = meterRegistry;
  }

  /**
   * Runs the piece's {@code postCheck[]} hooks off the request thread. Returns immediately —
   * submission is fire-and-forget; hook latency never delays the response.
   *
   * @param piece
   *          the manifest piece that just executed successfully
   * @param principal
   *          the guard's per-principal identity (block scoping)
   * @param actor
   *          the audit actor resolved on the request thread, or {@code null} to re-resolve
   * @param correlationId
   *          the caller-supplied correlation id, when valid
   * @param context
   *          the successful-execution snapshot
   */
  public void onSuccess(
          Piece piece,
          String principal,
          @Nullable String actor,
          @Nullable String correlationId,
          InvariantContext context) {
    if (piece.postCheck().isEmpty()) {
      return;
    }
    PostCheckHook.Invocation invocation = new PostCheckHook.Invocation(piece, principal, actor,
            correlationId, context);
    executor.execute(() -> runHooks(invocation));
  }

  private void runHooks(PostCheckHook.Invocation invocation) {
    Piece piece = invocation.piece();
    boolean ridPut = invocation.correlationId() != null;
    if (ridPut) {
      MDC.put(StarterConstants.REQUEST_ID_MDC_KEY, invocation.correlationId());
    }
    try {
      for (PostCheck check : piece.postCheck()) {
        runOne(check, invocation);
      }
    } finally {
      if (ridPut) {
        MDC.remove(StarterConstants.REQUEST_ID_MDC_KEY);
      }
    }
  }

  private void runOne(PostCheck check, PostCheckHook.Invocation invocation) {
    PostCheckHook hook = hooksByType.get(check.type());
    try {
      if (hook == null) {
        throw new IllegalArgumentException(
                "no PostCheckHook registered for type '" + check.type() + "'");
      }
      hook.run(check, invocation);
      count(StarterConstants.POSTCHECK_TOTAL_COUNTER, check.type());
    } catch (Exception e) {
      count(StarterConstants.POSTCHECK_TOTAL_COUNTER, check.type());
      count(StarterConstants.POSTCHECK_FAILED_COUNTER, check.type());
      log.error("[Piece post-check] {} hook FAILED for piece '{}' (correlationId={}): {}",
              check.type(), invocation.piece().id(), invocation.correlationId(),
              e.getMessage(), e);
      auditFailure(check, invocation, e);
      if (PostCheck.ON_FAILURE_BLOCK.equals(check.onFailure())) {
        blockRegistry.block(invocation.piece().id(), invocation.principal(),
                check.type() + " hook failed: " + e.getMessage());
      }
    }
  }

  private void auditFailure(PostCheck check, PostCheckHook.Invocation invocation, Exception e) {
    DslAuditService auditService = auditServiceProvider == null
            ? null
            : auditServiceProvider.getIfAvailable();
    if (auditService == null) {
      return;
    }
    String actor = invocation.actor() != null ? invocation.actor() : DslAuditService.currentActor();
    try {
      auditService.record(actor, StarterConstants.ACTION_PIECE_POSTCHECK_FAILURE,
              invocation.piece().id(), invocation.correlationId(),
              StarterConstants.OUTCOME_FAILURE,
              Map.of("hook", check.type(), "onFailure", check.onFailure(),
                      "error", String.valueOf(e.getMessage())));
    } catch (Exception auditError) {
      // Belt-and-braces: the real DslAuditService is fail-safe, but a failure audit must never
      // mask the hook failure it describes.
      log.warn("[Piece post-check] failure-audit row for piece '{}' could not be written: {}",
              invocation.piece().id(), auditError.getMessage());
    }
  }

  private void count(String counter, String hookType) {
    if (meterRegistry == null) {
      return;
    }
    meterRegistry.counter(counter, "hook", hookType).increment();
  }
}
