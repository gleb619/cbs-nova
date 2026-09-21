
public class OrderIdGenerationModels {

  public record OrderIdGenerationIn(String orderId, String namespace) {
  }

  public record OrderIdGenerationOut(
          String orderId,
          String randomId,
          String namespacedId1,
          String namespacedId2,
          boolean deterministicTailMatch) {
  }
}
