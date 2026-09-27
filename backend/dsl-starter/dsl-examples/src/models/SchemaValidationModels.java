public class SchemaValidationModels {

  public record SchemaValidationIn(
          String schema,
          String validPayload,
          String invalidPayload,
          String failFastPayload,
          Boolean failFast) {
  }

  public record SchemaValidationOut(
          String schema,
          boolean validResult,
          int validErrorCount,
          boolean invalidResult,
          int invalidErrorCount,
          String firstInvalidPath,
          String firstInvalidMessage,
          boolean failFastResult,
          int failFastErrorCount,
          String firstFailFastPath,
          String firstFailFastMessage) {
  }
}
