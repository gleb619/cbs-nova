package cbs.nova.starter.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Unit specs for {@link InMemoryRateLimitStore}. Uses a mutable fake clock ({@link AtomicLong})
 * instead of {@link Thread#sleep} so every assertion is deterministic.
 *
 * <p>
 * All math values are pinned to the exact source-verified semantics — no behaviour is "fixed" here.
 * See {@code docs/plans/T742-inmemory-ratelimit-spec.md} for the contract.
 */
class InMemoryRateLimitStoreTest {

  /** Mutable nano-clock; advance via {@link #advanceNanos(long)}. */
  private static final class FakeClock {
    private final AtomicLong nowNanos = new AtomicLong(0L);

    long nanoTime() {
      return nowNanos.get();
    }

    void advanceNanos(long delta) {
      nowNanos.addAndGet(delta);
    }
  }

  private static InMemoryRateLimitStore newStore(FakeClock clock) {
    return new InMemoryRateLimitStore(clock::nanoTime);
  }

  private static long nanosPerSecond() {
    return 1_000_000_000L;
  }

  @Test
  void firstHitOnKeyStartsBucketAtCapacityAndConsumesOne() {
    FakeClock clock = new FakeClock();
    InMemoryRateLimitStore store = newStore(clock);
    RateLimit limit = new RateLimit(20, 5.0);

    Consumption first = store.consume("k", limit);

    assertThat(first.allowed()).isTrue();
    assertThat(first.retryAfterSeconds()).isZero();
  }

  @Test
  void burstToCapacityThenDenyWithExpectedRetryAfter() {
    FakeClock clock = new FakeClock();
    InMemoryRateLimitStore store = newStore(clock);
    RateLimit limit = new RateLimit(20, 5.0);

    // Burn the full bucket (capacity=20 hits all allowed).
    for (int i = 0; i < 20; i++) {
      assertThat(store.consume("k", limit).allowed())
              .as("hit %d/20", i + 1)
              .isTrue();
    }
    // 21st hit: tokens=0, refill=0.
    Consumption denied = store.consume("k", limit);

    assertThat(denied.allowed()).isFalse();
    // raw = ceil((1.0 - 0.0) / 5.0) = ceil(0.2) = 1; max(1, 1) = 1.
    assertThat(denied.retryAfterSeconds()).isEqualTo(1L);
  }

  @Test
  void denyRetryAfterUsesMaxOneClampWhenRawCeilIsTiny() {
    // Math.ceil of a tiny positive double is 1, not 0 — so the max(1, …) clamp is what
    // produces 1 here. Pins the (max-1) clamp behaviour for the "raw ceil would be 0" case.
    FakeClock clock = new FakeClock();
    InMemoryRateLimitStore store = newStore(clock);
    // tokens will land just under 1.0 with a large rps so (1 - tokens)/rps is tiny.
    RateLimit limit = new RateLimit(2, 1_000.0);

    assertThat(store.consume("k", limit).allowed()).isTrue(); // tokens=2 -> 1
    assertThat(store.consume("k", limit).allowed()).isTrue(); // tokens=1 -> 0

    // No advance: tokens=0, raw = ceil(1.0 / 1000) = 1.
    Consumption denied = store.consume("k", limit);

    assertThat(denied.allowed()).isFalse();
    assertThat(denied.retryAfterSeconds()).isEqualTo(1L);
  }

  @Test
  void denyRetryAfterRoundsUpForFractionalWait() {
    FakeClock clock = new FakeClock();
    InMemoryRateLimitStore store = newStore(clock);
    RateLimit limit = new RateLimit(1, 1.0); // 1 token/sec

    assertThat(store.consume("k", limit).allowed()).isTrue();
    // No time passes: tokens=0, raw = ceil(1.0 / 1.0) = 1.
    Consumption denied = store.consume("k", limit);

    assertThat(denied.allowed()).isFalse();
    assertThat(denied.retryAfterSeconds()).isEqualTo(1L);
  }

  @Test
  void retryAfterScalesWithSlowerRefillRate() {
    FakeClock clock = new FakeClock();
    InMemoryRateLimitStore store = newStore(clock);
    RateLimit limit = new RateLimit(1, 0.5); // one token every 2 seconds

    assertThat(store.consume("k", limit).allowed()).isTrue();
    // tokens=0, raw = ceil(1.0 / 0.5) = ceil(2.0) = 2; max(1, 2) = 2.
    Consumption denied = store.consume("k", limit);

    assertThat(denied.allowed()).isFalse();
    assertThat(denied.retryAfterSeconds()).isEqualTo(2L);
  }

  @Test
  void refillClampedAtCapacityAfterLongIdlePeriod() {
    FakeClock clock = new FakeClock();
    InMemoryRateLimitStore store = newStore(clock);
    RateLimit limit = new RateLimit(5, 10.0);

    // Drain the bucket.
    for (int i = 0; i < 5; i++) {
      assertThat(store.consume("k", limit).allowed()).isTrue();
    }
    assertThat(store.consume("k", limit).allowed()).isFalse();

    // Idle for an hour: raw refill = 3600 * 10 = 36000 tokens, but cap = 5.
    clock.advanceNanos(3600L * nanosPerSecond());

    // Capacity is 5, so exactly 5 consumes succeed before the next deny.
    for (int i = 0; i < 5; i++) {
      assertThat(store.consume("k", limit).allowed())
              .as("hit %d/5 after idle", i + 1)
              .isTrue();
    }
    assertThat(store.consume("k", limit).allowed()).isFalse();
  }

  @Test
  void partialRefillCrossingOneBoundaryAllowsExactlyOneHit() {
    FakeClock clock = new FakeClock();
    InMemoryRateLimitStore store = newStore(clock);
    RateLimit limit = new RateLimit(1, 1.0);

    assertThat(store.consume("k", limit).allowed()).isTrue(); // tokens=1 -> 0
    // Advance 0.5s: refill = 0.5 * 1.0 = 0.5. tokens = 0 + 0.5 = 0.5. < 1.0 -> deny.
    clock.advanceNanos(nanosPerSecond() / 2);
    assertThat(store.consume("k", limit).allowed()).isFalse();

    // Advance another 0.5s: refill since lastRefill = 0.5 * 1.0 = 0.5.
    // tokens = min(1, 0.5 + 0.5) = 1.0 -> consume allowed, tokens -> 0.
    clock.advanceNanos(nanosPerSecond() / 2);
    assertThat(store.consume("k", limit).allowed()).isTrue();
    assertThat(store.consume("k", limit).allowed()).isFalse();
  }

  @Test
  void partialRefillBelowOneStillDenies() {
    FakeClock clock = new FakeClock();
    InMemoryRateLimitStore store = newStore(clock);
    RateLimit limit = new RateLimit(1, 1.0);

    assertThat(store.consume("k", limit).allowed()).isTrue();
    // Advance 0.4s: refill = 0.4, tokens = 0.4. < 1.0 -> deny.
    clock.advanceNanos((long) (0.4 * nanosPerSecond()));
    Consumption denied = store.consume("k", limit);

    assertThat(denied.allowed()).isFalse();
    // raw = ceil((1.0 - 0.4) / 1.0) = ceil(0.6) = 1.
    assertThat(denied.retryAfterSeconds()).isEqualTo(1L);
  }

  @Test
  void independentKeysDoNotShareBudget() {
    FakeClock clock = new FakeClock();
    InMemoryRateLimitStore store = newStore(clock);
    RateLimit limit = new RateLimit(1, 1.0);

    assertThat(store.consume("tenant-a|/api/x", limit).allowed()).isTrue();
    assertThat(store.consume("tenant-a|/api/x", limit).allowed()).isFalse();
    // Different key still has a full token.
    assertThat(store.consume("tenant-b|/api/x", limit).allowed()).isTrue();
    // Same tenant, different route class: independent bucket.
    assertThat(store.consume("tenant-a|/api/y", limit).allowed()).isTrue();
  }

  @Test
  void rateClassDefaultsFromManifestPropertiesAreConsistent() {
    // Cross-check: CbsDslManifestProperties defaults are capacity=20, refillPerSecond=5.0.
    // One full burst should be allowed and the next call denied with retryAfter=1.
    FakeClock clock = new FakeClock();
    InMemoryRateLimitStore store = newStore(clock);
    RateLimit manifestDefaults = new RateLimit(20, 5.0);

    for (int i = 0; i < 20; i++) {
      assertThat(store.consume("manifest-default", manifestDefaults).allowed())
              .as("hit %d/20", i + 1)
              .isTrue();
    }
    Consumption denied = store.consume("manifest-default", manifestDefaults);

    assertThat(denied.allowed()).isFalse();
    assertThat(denied.retryAfterSeconds()).isEqualTo(1L);

    // After 0.2s, exactly one more token refilled (0.2 * 5 = 1.0).
    clock.advanceNanos((long) (0.2 * nanosPerSecond()));
    assertThat(store.consume("manifest-default", manifestDefaults).allowed()).isTrue();
    assertThat(store.consume("manifest-default", manifestDefaults).allowed()).isFalse();
  }

  // --- idle-bucket eviction (T791) -----------------------------------------------------

  /** 1024 consumes on a dedicated key force the amortized sweep at least once. */
  private static void forceSweep(InMemoryRateLimitStore store, RateLimit limit) {
    for (int i = 0; i < 1024; i++) {
      store.consume("__sweep__", limit);
    }
  }

  @Test
  void idleBucketIsEvictedAfterFullRefillWindow() {
    FakeClock clock = new FakeClock();
    InMemoryRateLimitStore store = newStore(clock);
    // Full-refill window = max(60s floor, 20/5 = 4s) = 60s.
    RateLimit limit = new RateLimit(20, 5.0);

    assertThat(store.consume("idle", limit).allowed()).isTrue();
    clock.advanceNanos(61L * nanosPerSecond());

    forceSweep(store, limit);

    // "idle" evicted; only the sweep-probe bucket remains.
    assertThat(store.bucketCount()).isEqualTo(1);

    // Behaviour-neutral: evicted-after-full-refill behaves exactly like a fresh bucket.
    for (int i = 1; i <= 20; i++) {
      assertThat(store.consume("idle", limit).allowed())
              .as("hit %d/20 after eviction", i + 1)
              .isTrue();
    }
    assertThat(store.consume("idle", limit).allowed()).isFalse();
  }

  @Test
  void activeBucketIsKeptBySweep() {
    FakeClock clock = new FakeClock();
    InMemoryRateLimitStore store = newStore(clock);
    RateLimit limit = new RateLimit(20, 5.0);

    assertThat(store.consume("active", limit).allowed()).isTrue();
    // 10s < 60s floor: bucket is still inside its idle window.
    clock.advanceNanos(10L * nanosPerSecond());

    forceSweep(store, limit);

    assertThat(store.bucketCount()).isEqualTo(2);
    // Refill accrued during the 10s idle is preserved (tokens clamped at capacity).
    for (int i = 1; i <= 20; i++) {
      assertThat(store.consume("active", limit).allowed())
              .as("hit %d/20", i)
              .isTrue();
    }
    assertThat(store.consume("active", limit).allowed()).isFalse();
  }

  @Test
  void exhaustedBucketIsNotEvictedBeforeItWouldHaveRefilled() {
    FakeClock clock = new FakeClock();
    InMemoryRateLimitStore store = newStore(clock);
    RateLimit limit = new RateLimit(1, 1.0);

    assertThat(store.consume("k", limit).allowed()).isTrue(); // tokens -> 0
    assertThat(store.consume("k", limit).allowed()).isFalse(); // denied, lastRefill = now

    // Half the (1s) refill time, far below the 60s idle floor: must NOT be evicted.
    clock.advanceNanos(nanosPerSecond() / 2);

    forceSweep(store, limit);

    assertThat(store.bucketCount()).isEqualTo(2);
    // Partial refill (0.5 token) is preserved — not reset to a fresh full bucket.
    Consumption denied = store.consume("k", limit);
    assertThat(denied.allowed()).isFalse();
    assertThat(denied.retryAfterSeconds()).isEqualTo(1L);
  }

  @Test
  void hardCapEvictsOldestBucketWhenExceeded() {
    FakeClock clock = new FakeClock();
    InMemoryRateLimitStore store = new InMemoryRateLimitStore(clock::nanoTime, 2);
    RateLimit limit = new RateLimit(1, 1.0);

    assertThat(store.consume("a", limit).allowed()).isTrue(); // t=0, oldest
    clock.advanceNanos(1_000_000L); // 1ms
    assertThat(store.consume("b", limit).allowed()).isTrue(); // t=1ms
    clock.advanceNanos(1_000_000L); // 1ms
    assertThat(store.consume("c", limit).allowed()).isTrue(); // size 3 > cap 2 -> eager sweep

    assertThat(store.bucketCount()).isEqualTo(2);
    // "b" kept -> ~2ms refill (0.002 tokens) is not enough for a hit.
    assertThat(store.consume("b", limit).allowed()).isFalse();
    // "a" (oldest lastRefillNanos) was evicted -> fresh bucket allows exactly one hit.
    assertThat(store.consume("a", limit).allowed()).isTrue();
  }
}
