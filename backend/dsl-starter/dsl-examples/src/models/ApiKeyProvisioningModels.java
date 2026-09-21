public class ApiKeyProvisioningModels {

  public record ApiKeyProvisioningIn(String purpose) {
  }

  public record ApiKeyProvisioningOut(
          String purpose,
          String apiKey,
          String idempotencyKey) {
  }
}
