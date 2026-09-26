public class DistributedTracingModels {

  public record DistributedTracingIn(String businessKey) {
  }

  public record DistributedTracingOut(String traceparent, String extractedSpanId, String baggageValue) {
  }
}
