package cbs.nova.dsl;

import java.time.Duration;

public record RetryPolicy(int maxAttempts, Duration initialInterval, double backoffCoefficient) {
  private static final RetryPolicy INSTANCE = new RetryPolicy(0, Duration.ZERO, 0);

  public static RetryPolicy getInstance() {
    return INSTANCE;
  }

  public RetryPolicy defaults() {
    return new RetryPolicy(3, Duration.ofSeconds(1), 2.0);
  }
}
