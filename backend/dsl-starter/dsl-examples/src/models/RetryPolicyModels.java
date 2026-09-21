import java.util.List;

public class RetryPolicyModels {

  public record RetryPolicyIn(int maxAttempts, long baseMillis, long maxMillis) {
  }

  public record RetryPolicyOut(
          int maxAttempts,
          long baseMillis,
          long maxMillis,
          List<Long> noneDelays,
          long fullDelay) {
  }
}