package cbs.nova.starter.service.check;

import cbs.nova.starter.model.InvariantContext;
import cbs.nova.starter.model.PostCheck;

/**
 * {@code postCheck: invariant-assert} — evaluates the check's {@code expr} against the
 * {@link InvariantContext}.
 *
 * <p>
 * Deliberately conservative: {@code expr} is one of a small set of named conditions, not an
 * expression language —
 *
 * <ul>
 * <li>{@code success} (also the default when {@code expr} is absent/blank) — status
 * {@code < 400}</li>
 * <li>{@code status-2xx} — status in {@code [200, 300)}</li>
 * <li>{@code status-3xx} — status in {@code [300, 400)}</li>
 * </ul>
 *
 * <p>
 * A violated assertion throws — the pipeline then applies the check's {@code onFailure} policy
 * (warn / block-next-execution). An unrecognized {@code expr} also throws: an invariant nobody can
 * evaluate is a failure, not a pass.
 */
public class InvariantAssertHook implements PostCheckHook {

  static final String COND_SUCCESS = "success";
  static final String COND_2XX = "status-2xx";
  static final String COND_3XX = "status-3xx";

  @Override
  public String type() {
    return "invariant-assert";
  }

  @Override
  public void run(PostCheck check, Invocation invocation) throws Exception {
    if (!(check instanceof PostCheck.InvariantAssertCheck invariant)) {
      throw new IllegalArgumentException(
              "invariant-assert hook given a " + check.type() + " check");
    }
    InvariantContext context = invocation.context();
    String expr = invariant.expr() == null || invariant.expr().isBlank()
            ? COND_SUCCESS
            : invariant.expr().trim();
    boolean pass = switch (expr) {
      case COND_SUCCESS -> context.status() < 400;
      case COND_2XX -> context.status() >= 200 && context.status() < 300;
      case COND_3XX -> context.status() >= 300 && context.status() < 400;
      default -> throw new IllegalArgumentException(
              "unknown invariant-assert expr '" + invariant.expr() + "'; expected one of "
                      + COND_SUCCESS + ", " + COND_2XX + ", " + COND_3XX);
    };
    if (!pass) {
      String detail = invariant.description() != null && !invariant.description().isBlank()
              ? invariant.description()
              : "invariant '" + expr + "' violated";
      throw new IllegalStateException("invariant-assert failed for piece '" + context.pieceId()
              + "' (status " + context.status() + "): " + detail);
    }
  }
}
