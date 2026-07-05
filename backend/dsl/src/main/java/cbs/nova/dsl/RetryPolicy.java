package cbs.nova.dsl;

import java.time.Duration;

public record RetryPolicy(int maxAttempts, Duration initialInterval, double backoffCoefficient) {
  public static RetryPolicy defaults() {
    return new RetryPolicy(3, Duration.ofSeconds(1), 2.0);
  }
}
