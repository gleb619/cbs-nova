public class OrderMetricsModels {

  public record OrderMetricsIn(String orderId, String productCategory, int quantity) {
  }

  public record OrderMetricsOut(String orderId, String status, long processingTimeMs) {
  }
}
