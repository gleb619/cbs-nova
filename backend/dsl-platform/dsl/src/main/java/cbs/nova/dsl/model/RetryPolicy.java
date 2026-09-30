package cbs.nova.dsl.model;

import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record RetryPolicy(
        int maxAttempts,
        @NonNull Duration initialInterval,
        double backoffCoefficient,
        @Nullable Duration maximumInterval,
        @Nullable List<@NonNull String> nonRetryable) {

  public RetryPolicy(int maxAttempts, @NonNull Duration initialInterval,
          double backoffCoefficient) {
    this(maxAttempts, initialInterval, backoffCoefficient, null, null);
  }

  public RetryPolicy {
    if (maxAttempts < 1) {
      throw new IllegalArgumentException(
              "maxAttempts must be >= 1 (got " + maxAttempts + ")");
    }
    if (backoffCoefficient <= 0.0) {
      throw new IllegalArgumentException(
              "backoffCoefficient must be > 0 (got " + backoffCoefficient + ")");
    }
    if (maximumInterval != null && maximumInterval.compareTo(initialInterval) < 0) {
      throw new IllegalArgumentException(
              "maximumInterval (" + maximumInterval + ") must be >= initialInterval ("
                      + initialInterval + ")");
    }
    if (nonRetryable != null) {
      for (String name : nonRetryable) {
        if (name == null || name.isBlank()) {
          throw new IllegalArgumentException("nonRetryable names must be non-blank");
        }
      }
    }
  }

  public @NonNull RetryPolicy withMaximumInterval(@Nullable Duration value) {
    return new RetryPolicy(maxAttempts, initialInterval, backoffCoefficient, value, nonRetryable);
  }

  public @NonNull RetryPolicy withNonRetryable(@Nullable String... types) {
    return new RetryPolicy(
            maxAttempts, initialInterval, backoffCoefficient, maximumInterval,
            types == null ? null : List.of(types));
  }

  public @NonNull RetryPolicy withNonRetryable(@Nullable List<@NonNull String> types) {
    return new RetryPolicy(
            maxAttempts, initialInterval, backoffCoefficient, maximumInterval,
            types == null ? null : List.copyOf(types));
  }
}
