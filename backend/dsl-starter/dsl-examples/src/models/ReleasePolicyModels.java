public class ReleasePolicyModels {

  public record ReleasePolicyIn(String incomingVersion, String requiredRange, String bumpType,
      String buildMetadata) {
  }

  public record ReleasePolicyOut(boolean accepted, int major, int minor, int patch,
      boolean rangeSatisfied, String bumpedVersion, String nextCandidate) {
  }
}
