import java.util.List;
import java.util.Map;

public class SafeTemplatingModels {

  public record SafeTemplatingIn(
      Map<String, Object> order,
      String greetingTemplate) {
  }

  public record SafeTemplatingOut(
      String nestedPathResult,
      String defaultValueResult,
      String escapedLiteralResult,
      String missingKeyResult,
      List<String> resolvedKeys,
      String trustedGreeting) {
  }
}
