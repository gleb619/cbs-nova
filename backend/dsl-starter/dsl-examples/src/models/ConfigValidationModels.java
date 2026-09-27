public class ConfigValidationModels {

  public record ConfigValidationIn(
      String configYaml,
      String schemaJson) {
  }

  public record ConfigValidationOut(
      boolean schemaValid,
      int schemaErrorCount,
      boolean versionFormatValid,
      String parsedReleaseDate,
      String formattedThreshold,
      String canonicalYaml) {
  }
}
