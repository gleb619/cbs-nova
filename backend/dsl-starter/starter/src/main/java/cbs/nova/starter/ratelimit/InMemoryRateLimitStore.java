package cbs.nova.starter.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Per-instance token-bucket store. This is the original {@link RateLimitFilter} behaviour,
 * extracted into a reusable store and kept as the dev / single-node fallback.
 */
public final class InMemoryRateLimitStore implements RateLimitStore {

  private static final long NANOS_PER_SECOND = 1_000_000_000L;

  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
  private final LongSupplier nanoTime;

  public InMemoryRateLimitStore(LongSupplier nanoTime) {
    this.nanoTime = nanoTime;
  }

  @Override
  public Consumption consume(String key, RateLimit rateLimit) {
    long now = nanoTime.getAsLong();
    Bucket bucket = buckets.compute(key, (ignored, current) -> {
      Bucket baseline = current == null
              ? new Bucket(rateLimit.capacity(), now, false)
              : current;
      long elapsedNanos = now - baseline.lastRefillNanos();
      double refill = elapsedNanos * rateLimit.refillPerSecond() / NANOS_PER_SECOND;
      double tokens = Math.min(rateLimit.capacity(), baseline.tokens() + refill);
      if (tokens >= 1.0) {
        return new Bucket(tokens - 1.0, now, true);
      }
      return new Bucket(tokens, now, false);
    });
    return bucket.toConsumption(rateLimit.refillPerSecond());
  }

  private record Bucket(double tokens, long lastRefillNanos, boolean consumed) {

    Consumption toConsumption(double refillPerSecond) {
      if (consumed) {
        return new Consumption(true, 0L);
      }
      long retryAfterSeconds = Math.max(1L, (long) Math.ceil((1.0 - tokens) / refillPerSecond));
      return new Consumption(false, retryAfterSeconds);
    }
  }
}
