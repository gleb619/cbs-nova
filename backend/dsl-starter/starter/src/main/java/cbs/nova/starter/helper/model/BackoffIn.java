package cbs.nova.starter.helper.model;

import org.jspecify.annotations.Nullable;

public record BackoffIn(
        @Nullable Integer attempt,
        @Nullable Long baseMillis,
        @Nullable Long maxMillis,
        @Nullable String jitter,
        @Nullable Long previousDelay) {

  public int effectiveAttempt() {
    return attempt == null ? 0 : attempt;
  }

  public long effectiveBase() {
    return baseMillis == null ? 1000L : baseMillis;
  }

  public long effectiveMax() {
    return maxMillis == null ? 60000L : maxMillis;
  }

  public String effectiveJitter() {
    return jitter == null || jitter.isBlank() ? "full" : jitter;
  }

  public long effectivePrevious() {
    return previousDelay == null ? -1L : previousDelay;
  }
}
