import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Input/output models for the {@code PreviewExplain} process and its helper transactions.
 *
 * <p>The process demonstrates how a single {@code Dsl.process(...)} definition can carry a
 * real run path, a custom {@code .preview(...)} that returns a computed plan without side
 * effects, and a custom {@code .explain(...)} that returns an {@code ExplainReport} entirely
 * built in code (no markdown resource file).
 *
 * <p>{@link #PERSIST_COUNTER} is the single source of truth the smoke test uses to assert
 * the preview/explain paths do not invoke the write transaction: it only increments from
 * the transaction's {@code execute} lambda, so a value of {@code 0} after a preview or
 * explain run proves the preview/explain code paths never touched the side-effecting
 * transaction body.
 */
public class PreviewExplainModels {

  /** Number of times the write transaction's {@code execute} lambda has actually run. */
  public static final AtomicInteger PERSIST_COUNTER = new AtomicInteger();

  /**
   * Inbound payload for the process: a list of priced line items plus a discount rate
   * (0.0–1.0) applied to the subtotal before tax.
   */
  public record PreviewExplainIn(
      String orderId,
      List<LineItem> lines,
      double discountRate) {
  }

  /** Single priced line on the order. */
  public record LineItem(String sku, String description, double unitPrice, int quantity) {
  }

  /**
   * Computed plan returned by both run mode and preview mode. The fields are intentionally
   * structured (subtotal / discount / tax / total / per-line breakdown) so a downstream
   * reviewer can read the plan without re-deriving it from the input.
   */
  public record PreviewExplainOut(
      String orderId,
      List<PlanLine> plan,
      double subtotal,
      double discount,
      double tax,
      double total) {
  }

  /** Plan entry: the same shape as {@link LineItem} plus the extended price. */
  public record PlanLine(String sku, String description, int quantity, double unitPrice, double extended) {
  }

  /** Argument for the {@code persistPlan} transaction: only carries the order id. */
  public record PersistPlanIn(String orderId) {
  }

  /** Acknowledgement returned by the {@code persistPlan} transaction. */
  public record PersistPlanOut(String orderId, boolean persisted) {
  }
}