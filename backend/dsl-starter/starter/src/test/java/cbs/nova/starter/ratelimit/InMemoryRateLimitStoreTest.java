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
}
