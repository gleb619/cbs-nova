
import java.util.Map;

public class MaskSensitiveDataModels {

  public record MaskingIn(
      String cardNumber,
      String payerReference,
      String notificationTemplate,
      long amount) {
  }

  public record MaskingOut(
      String maskedCard,
      String notificationMessage,
      Map<String, Object> auditDetails) {
  }
}
