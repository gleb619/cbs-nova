package cbs.nova.starter.ratelimit;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import lombok.RequiredArgsConstructor;

/**
 * Per-instance token-bucket store. This is the original {@link RateLimitFilter} behaviour,
 * extracted into a reusable store and kept as the dev / single-node fallback.
 *
 * <p>
 * Buckets never shrink on their own unless they are swept: an idle bucket that has been untouched
 * for at least its full-refill window ({@code capacity / refillPerSecond}, with a 60-second floor)
 * is indistinguishable from a fresh one, so the sweep drops it. This bounds memory growth under
 * source-IP scans. The sweep is lazy and amortized — every {@value #SWEEP_INTERVAL}th
 * {@code consume}, or immediately when the hard cap is exceeded — and never holds a global lock:
 * removals are conditional {@code remove(key, value)} calls, safe against concurrent updates.
 */
@RequiredArgsConstructor
public final class InMemoryRateLimitStore implements RateLimitStore {

  private static final long NANOS_PER_SECOND = 1_000_000_000L;
  /** Floor for the idle window: a bucket is swept only after 60 s of inactivity at minimum. */
  private static final long MIN_IDLE_NANOS = 60L * NANOS_PER_SECOND;
  /** Amortized sweep cadence: every Nth consume, plus whenever the cap is exceeded. */
  private static final int SWEEP_INTERVAL = 1024;
  static final int DEFAULT_MAX_BUCKETS = 100_000;

  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
  private final LongSupplier nanoTime;
  private final int maxBuckets;
  private final AtomicLong consumeCount = new AtomicLong();

  public InMemoryRateLimitStore(LongSupplier nanoTime) {
    this(nanoTime, DEFAULT_MAX_BUCKETS);
  }

  @Override
  public Consumption consume(String key, RateLimit rateLimit) {
    long now = nanoTime.getAsLong();
    Bucket bucket = buckets.compute(key, (ignored, current) -> {
      Bucket baseline = current == null
              ? new Bucket(rateLimit.capacity(), now, false,
                      rateLimit.capacity(), rateLimit.refillPerSecond())
              : current;
      long elapsedNanos = now - baseline.lastRefillNanos();
      double refill = elapsedNanos * rateLimit.refillPerSecond() / NANOS_PER_SECOND;
      double tokens = Math.min(rateLimit.capacity(), baseline.tokens() + refill);
      if (tokens >= 1.0) {
        return new Bucket(tokens - 1.0, now, true,
                baseline.capacity(), baseline.refillPerSecond());
      }
      return new Bucket(tokens, now, false, baseline.capacity(), baseline.refillPerSecond());
    });
    sweepIfDue(now);
    return bucket.toConsumption(rateLimit.refillPerSecond());
  }

  private void sweepIfDue(long now) {
    long count = consumeCount.incrementAndGet();
    if (count % SWEEP_INTERVAL != 0 && buckets.size() <= maxBuckets) {
      return;
    }
    sweep(now);
  }

  private void sweep(long now) {
    buckets.forEach((key, bucket) -> {
      if (now - bucket.lastRefillNanos() >= bucket.idleWindowNanos()) {
        buckets.remove(key, bucket);
      }
    });
    int overflow = buckets.size() - maxBuckets;
    if (overflow <= 0) {
      return;
    }
    // Still over the cap after dropping idle buckets: evict the oldest lastRefillNanos entries.
    buckets.entrySet().stream()
            .sorted(Comparator.comparingLong(entry -> entry.getValue().lastRefillNanos()))
            .limit(overflow)
            .forEach(entry -> buckets.remove(entry.getKey(), entry.getValue()));
  }

  /** Package-private test accessor — not part of the public API. */
  int bucketCount() {
    return buckets.size();
  }

  private record Bucket(double tokens, long lastRefillNanos, boolean consumed,
          double capacity, double refillPerSecond) {

    /**
     * Time of inactivity after which this bucket is guaranteed fully refilled and therefore
     * behaviour-neutral to evict.
     */
    long idleWindowNanos() {
      if (refillPerSecond <= 0.0) {
        return Long.MAX_VALUE;
      }
      long window = (long) Math.ceil(capacity / refillPerSecond * NANOS_PER_SECOND);
      return Math.max(MIN_IDLE_NANOS, window);
    }

    Consumption toConsumption(double refillPerSecond) {
      if (consumed) {
        return new Consumption(true, 0L);
      }
      long retryAfterSeconds = Math.max(1L, (long) Math.ceil((1.0 - tokens) / refillPerSecond));
      return new Consumption(false, retryAfterSeconds);
    }
  }
}
