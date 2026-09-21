import java.util.List;

public class SampleDataGenerationModels {

  public record SampleDataGenerationIn(String prefix) {
  }

  public record SampleDataGenerationOut(
          String orderId,
          int customerId,
          double amount,
          String priority,
          String region,
          List<String> tags) {
  }
}