public class SignalBranchModels {

  public record SignalBranchIn(String requestId, int amount) {
  }

  public record SignalBranchOut(String decision, String requestId, boolean signalSeen) {
  }
}