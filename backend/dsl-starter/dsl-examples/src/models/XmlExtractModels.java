public class XmlExtractModels {

  public record ExtractIn(String primaryXpath, String optionalXpath) {
  }

  public record ExtractOut(
      String primaryValue,
      boolean primaryPresent,
      String optionalValue,
      boolean optionalPresent) {
  }
}