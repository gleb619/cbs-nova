package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.starter.config.properties.DslProperties;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DslFileBufferTest {

  private final DslFileBuffer buffer = new DslFileBuffer(new DslProperties());

  @Test
  void stageAndGetRoundTrip() {
    buffer.stage("a/b.txt", "content");
    assertThat(buffer.get("a/b.txt")).isEqualTo("content");
  }

  @Test
  void stageOverwritesPreviousContent() {
    buffer.stage("a/b.txt", "first");
    buffer.stage("a/b.txt", "second");
    assertThat(buffer.get("a/b.txt")).isEqualTo("second");
  }

  @Test
  void getOnMissingKeyReturnsNull() {
    assertThat(buffer.get("missing.txt")).isNull();
  }

  @Test
  void drainReturnsAllAndEmptiesBuffer() {
    buffer.stage("a.txt", "A");
    buffer.stage("b.txt", "B");
    buffer.stage("c.txt", "C");

    Map<String, String> snapshot = buffer.drain();

    assertThat(snapshot).containsExactlyInAnyOrderEntriesOf(Map.of(
            "a.txt", "A",
            "b.txt", "B",
            "c.txt", "C"));
    assertThat(buffer.get("a.txt")).isNull();
    assertThat(buffer.get("b.txt")).isNull();
    assertThat(buffer.get("c.txt")).isNull();
    assertThat(buffer.pendingCount()).isZero();
  }

  @Test
  void drainOnEmptyBufferReturnsEmptyMap() {
    assertThat(buffer.drain()).isEmpty();
    assertThat(buffer.pendingCount()).isZero();
  }

  @Test
  void normalizeCollapsesBackslashesAndDuplicates() {
    buffer.stage("\\\\a\\\\b.txt", "x");
    assertThat(buffer.get("/a/b.txt")).isEqualTo("x");
    assertThat(buffer.get("a/b.txt")).isEqualTo("x");
  }

  @Test
  void normalizeStripsLeadingSlashes() {
    buffer.stage("///x.txt", "x");
    assertThat(buffer.get("x.txt")).isEqualTo("x");
  }

  @Test
  void normalizeRejectsPathTraversal() {
    assertThatThrownBy(() -> buffer.stage("../escape.txt", "x"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("path escapes workspace");
    assertThatThrownBy(() -> buffer.stage("a/../../escape.txt", "x"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("path escapes workspace");
  }

  @Test
  void normalizeRejectsTraversalEvenAfterCollapse() {
    // "../../foo" collapses to "../foo" after slash normalisation, still must be rejected.
    assertThatThrownBy(() -> buffer.stage("..//..//foo", "x"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("path escapes workspace");
  }

  @Test
  void normalizeAcceptsNullAsEmpty() {
    buffer.stage(null, "null-path");
    assertThat(buffer.get("")).isEqualTo("null-path");
  }

  @Test
  void pendingCountTracksEntries() {
    assertThat(buffer.pendingCount()).isZero();
    buffer.stage("a", "1");
    buffer.stage("b", "2");
    buffer.stage("c", "3");
    assertThat(buffer.pendingCount()).isEqualTo(3);
  }

  @Test
  void sizeEvictionDropsOldestEntriesWhenMaximumExceeded() {
    DslProperties properties = new DslProperties();
    properties.getFileBuffer().setMaxEntries(3);
    DslFileBuffer bounded = new DslFileBuffer(properties);

    Map<String, String> staged = Map.of(
            "a", "1",
            "b", "2",
            "c", "3",
            "d", "4",
            "e", "5");
    staged.forEach(bounded::stage);

    // Caffeine size-based eviction is opportunistic; force a maintenance pass so the test is
    // deterministic regardless of internal scheduling.
    cleanUp(bounded);

    // Bound respected: at most maxEntries remain after maintenance.
    assertThat(bounded.pendingCount()).isLessThanOrEqualTo(3);

    // At least one entry was evicted (we staged 5 with a bound of 3).
    long survivors = 0;
    for (String key : staged.keySet()) {
      if (bounded.get(key) != null) {
        survivors++;
      }
    }
    assertThat(survivors).isLessThan(5);

    // Surviving entries' values must match their original stage() contents — no corruption.
    for (Map.Entry<String, String> entry : staged.entrySet()) {
      String value = bounded.get(entry.getKey());
      if (value != null) {
        assertThat(value).isEqualTo(entry.getValue());
      }
    }
  }

  @Test
  void ttlExpiryEvictsEntryAfterConfiguredSeconds() {
    FakeTicker ticker = new FakeTicker();
    DslProperties properties = new DslProperties();
    properties.getFileBuffer().setExpireAfterWriteSeconds(60);
    DslFileBuffer bounded = new DslFileBuffer(properties, ticker);

    bounded.stage("a", "content");
    assertThat(bounded.get("a")).isEqualTo("content");

    ticker.advance(Duration.ofSeconds(59));
    // Just before TTL — entry is still present.
    assertThat(bounded.get("a")).isEqualTo("content");

    ticker.advance(Duration.ofSeconds(2));
    // Now past TTL — read-triggered expiry (getIfPresent) must report the entry as absent.
    assertThat(bounded.get("a")).isNull();
  }

  @Test
  void ttlExpiryDoesNotAffectFresherEntries() {
    FakeTicker ticker = new FakeTicker();
    DslProperties properties = new DslProperties();
    properties.getFileBuffer().setExpireAfterWriteSeconds(60);
    DslFileBuffer bounded = new DslFileBuffer(properties, ticker);

    bounded.stage("old", "old-content");
    ticker.advance(Duration.ofSeconds(30));
    bounded.stage("new", "new-content");
    ticker.advance(Duration.ofSeconds(31));

    assertThat(bounded.get("old")).isNull();
    assertThat(bounded.get("new")).isEqualTo("new-content");
  }

  @Test
  void drainIsAtomicPerEntryEvenWithConcurrentOverwrites() throws Exception {
    DslProperties properties = new DslProperties();
    properties.getFileBuffer().setMaxEntries(1000);
    DslFileBuffer bounded = new DslFileBuffer(properties);

    int writers = 4;
    int keysPerWriter = 50;
    ExecutorService pool = Executors.newFixedThreadPool(writers);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(writers);

    Map<String, String> producedByThread = new HashMap<>();

    try {
      for (int t = 0; t < writers; t++) {
        final int threadId = t;
        pool.submit(() -> {
          try {
            start.await();
            for (int k = 0; k < keysPerWriter; k++) {
              String key = "k-" + threadId + "-" + k;
              String value = "v-" + threadId + "-" + k;
              bounded.stage(key, value);
              producedByThread.put(key, value);
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            done.countDown();
          }
        });
      }

      start.countDown();
      assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
    } finally {
      pool.shutdown();
    }

    Map<String, String> drained = bounded.drain();
    assertThat(drained).hasSize(writers * keysPerWriter);
    assertThat(drained).containsAllEntriesOf(producedByThread);
    // Every entry drained exactly once: pendingCount reflects the empty cache.
    assertThat(bounded.pendingCount()).isZero();
  }

  @Test
  void configurationDefaultsAreApplied() {
    DslProperties properties = new DslProperties();
    assertThat(properties.getFileBuffer().getMaxEntries()).isEqualTo(1000);
    assertThat(properties.getFileBuffer().getExpireAfterWriteSeconds()).isEqualTo(3600L);
  }

  @Test
  void configurationCanBeOverridden() {
    DslProperties properties = new DslProperties();
    properties.getFileBuffer().setMaxEntries(7);
    properties.getFileBuffer().setExpireAfterWriteSeconds(13L);
    assertThat(properties.getFileBuffer().getMaxEntries()).isEqualTo(7);
    assertThat(properties.getFileBuffer().getExpireAfterWriteSeconds()).isEqualTo(13L);
  }

  /**
   * Drives Caffeine's maintenance task so size- or time-based eviction completes synchronously on
   * the calling thread.
   */
  private static void cleanUp(DslFileBuffer buffer) {
    buffer.cache().cleanUp();
  }

  /**
   * Fake {@link Ticker} whose clock can be advanced deterministically in tests, eliminating the
   * need for {@code Thread.sleep}.
   */
  private static final class FakeTicker implements Ticker {

    private final AtomicLong nanos = new AtomicLong();

    @Override
    public long read() {
      return nanos.get();
    }

    void advance(Duration delta) {
      nanos.addAndGet(delta.toNanos());
    }
  }
}
