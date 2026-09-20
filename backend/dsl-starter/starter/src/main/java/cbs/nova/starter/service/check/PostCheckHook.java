package cbs.nova.starter.service.check;

import cbs.nova.starter.model.InvariantContext;
import cbs.nova.starter.model.Piece;
import cbs.nova.starter.model.PostCheck;
import org.jspecify.annotations.Nullable;

/**
 * One {@code postCheck} hook implementation (T550). Implementations are dispatched by
 * {@link PieceCheckPipeline} on the post-check executor — never the request thread — and only after
 * the guarded piece executed successfully.
 *
 * <p>
 * Implementations must NOT swallow their own failures: any thrown exception is governed by the
 * check's {@code onFailure} policy (warn / block-next-execution) in the pipeline. The one exception
 * is {@code AuditWriteHook}, which is inherently fail-safe because {@link DslAuditService} already
 * warns-and-swallows internally.
 */
public interface PostCheckHook {

  String type();

  /**
   * Runs the hook. Throwing signals hook failure (assertion violated, sink unavailable, …).
   */
  void run(PostCheck check, Invocation invocation) throws Exception;

  /**
   * Everything a hook needs about the successful execution.
   *
   * @param piece
   *          the manifest piece that executed
   * @param principal
   *          the guard's per-principal identity (auth name / api-key marker / client IP) — used for
   *          {@code block-next-execution} scoping
   * @param actor
   *          the audit actor resolved on the request thread (T549-era
   *          {@link DslAuditService#currentActor()}), or {@code null} to re-resolve lazily
   * @param correlationId
   *          the caller-supplied correlation id, when valid
   * @param context
   *          the execution snapshot handed to {@code invariant-assert}
   */
  record Invocation(
          Piece piece,
          String principal,
          @Nullable String actor,
          @Nullable String correlationId,
          InvariantContext context) {
  }
}
