package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.BackoffIn;
import cbs.nova.starter.helper.model.BackoffOut;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.NonNull;

@Helper(name = "backoff")
public class BackoffHelper implements Executable<BackoffIn, BackoffOut> {

  @Override
  public @NonNull Result<BackoffOut> execute(@NonNull Context<BackoffIn> ctx) {
    try {
      BackoffIn input = ctx.body();
      int attempt = input.effectiveAttempt();
      long baseMillis = input.effectiveBase();
      long maxMillis = input.effectiveMax();
      String jitter = input.effectiveJitter().toLowerCase(Locale.ROOT);
      long previousDelay = input.effectivePrevious();

      validate(attempt, baseMillis, maxMillis, jitter, previousDelay);
      long cap = cappedDelay(attempt, baseMillis, maxMillis);
      long delay = switch (jitter) {
        case "none" -> cap;
        case "full" -> randomUpTo(cap);
        case "equal" -> cap / 2 + randomUpTo(cap / 2);
        case "decorrelated" -> decorrelated(baseMillis, maxMillis, previousDelay);
        default -> throw new IllegalArgumentException(
                "backoff.jitter must be one of: none, full, equal, decorrelated");
      };
      return Result.success(new BackoffOut(delay));
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static void validate(
          int attempt, long baseMillis, long maxMillis, String jitter, long previousDelay) {
    if (attempt < 0) {
      throw new IllegalArgumentException("backoff.attempt must be >= 0");
    }
    if (baseMillis <= 0) {
      throw new IllegalArgumentException("backoff.baseMillis must be > 0");
    }
    if (maxMillis <= 0) {
      throw new IllegalArgumentException("backoff.maxMillis must be > 0");
    }
    if (baseMillis > maxMillis) {
      throw new IllegalArgumentException("backoff.baseMillis must be <= maxMillis");
    }
    if (!switch (jitter) {
      case "none", "full", "equal", "decorrelated" -> true;
      default -> false;
    }) {
      throw new IllegalArgumentException(
              "backoff.jitter must be one of: none, full, equal, decorrelated");
    }
    if (jitter.equals("decorrelated") && previousDelay >= 0 && previousDelay < baseMillis) {
      throw new IllegalArgumentException("backoff.previousDelay must be >= baseMillis");
    }
  }

  private static long cappedDelay(int attempt, long baseMillis, long maxMillis) {
    if (attempt < 62 && baseMillis <= (maxMillis >> attempt)) {
      return baseMillis << attempt;
    }
    return maxMillis;
  }

  private static long randomUpTo(long upperInclusive) {
    return ThreadLocalRandom.current().nextLong(upperInclusive + 1);
  }

  private static long decorrelated(long baseMillis, long maxMillis, long previousDelay) {
    long previous = previousDelay < baseMillis ? baseMillis : previousDelay;
    long upper = previous > maxMillis / 3 ? maxMillis : previous * 3;
    long range = upper - baseMillis;
    return baseMillis + randomUpTo(range);
  }
}
