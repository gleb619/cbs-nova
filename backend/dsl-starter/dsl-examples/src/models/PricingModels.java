
import java.util.List;

public class PricingModels {

  public record OrderLine(String sku, int quantity, double unitPrice) {
  }

  public record OrderIn(String orderId, String customerTier, List<OrderLine> lines) {
  }

  public record LineTotalsOut(double subtotal) {
  }

  public record PricedOrder(
          String orderId,
          double subtotal,
          double discountRate,
          double discountAmount,
          double tax,
          double total) {
  }

  public record CheckoutReceipt(
          String orderId,
          String receiptNumber,
          double subtotal,
          double discountAmount,
          double tax,
          double total) {
  }

  public record QuoteOut(
          String orderId,
          String quoteRef,
          double subtotal,
          double discountAmount,
          double tax,
          double total) {
  }
}
