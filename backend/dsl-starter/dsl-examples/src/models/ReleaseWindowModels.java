public class ReleaseWindowModels {

  public record ReleaseIn(String currentVersion, String minimumVersion, String releaseDate,
      Long daysToAdd) {
  }

  public record ReleaseOut(boolean versionOk, String rolloutDate) {
  }
}
