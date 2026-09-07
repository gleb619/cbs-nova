public class JsonPatchModels {

  public record PatchIn(String sourceJson, String patchJson, String readPath) {
  }

  public record PatchOut(String patchedJson, String extractedValue, boolean present) {
  }
}
