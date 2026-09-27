
public class DataIntegrityModels {

  public record DataIntegrityIn(String payload) {
  }

  public record DataIntegrityOut(
      String digest,
      String encodedPayload) {
  }
}
