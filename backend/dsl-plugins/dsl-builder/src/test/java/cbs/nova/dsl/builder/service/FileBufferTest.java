package cbs.nova.dsl.builder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.builder.config.BuilderServiceConfiguration;
import cbs.nova.dsl.builder.config.DslBuilderProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class FileBufferTest {

  @Test
  void stageAndGetRoundTripsContent() {
    var harness = newHarness(100, 3600);

    harness.buffer().stage("dsl/A.java", "alpha");

    assertThat(harness.buffer().get("dsl/A.java")).isEqualTo("alpha");
  }

  @Test
  void getReturnsNullForUnknownPath() {
    var harness = newHarness(100, 3600);

    assertThat(harness.buffer().get("dsl/Missing.java")).isNull();
  }

  @Test
  void pendingCountReflectsStagedEntries() {
    var harness = newHarness(100, 3600);

    assertThat(harness.buffer().pendingCount()).isZero();
    harness.buffer().stage("dsl/A.java", "a");
    harness.buffer().stage("dsl/B.java", "b");
    assertThat(harness.buffer().pendingCount()).isEqualTo(2);
  }

  @Test
  void normalizeCollapsesBackslashesAndRepeatedSlashes() {
    var harness = newHarness(100, 3600);

    harness.buffer().stage("dsl\\Sub\\File.java", "x");

    assertThat(harness.buffer().get("dsl/Sub/File.java")).isEqualTo("x");
    assertThat(harness.buffer().get("///dsl//Sub///File.java")).isEqualTo("x");
    assertThat(harness.buffer().pendingCount()).isEqualTo(1);
  }

  @Test
  void normalizeRejectsPathEscape() {
    var harness = newHarness(100, 3600);

    assertThatThrownBy(() -> harness.buffer().stage("../escape.java", "x"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("path escapes workspace");
    assertThatThrownBy(() -> harness.buffer().stage("dsl/../escape.java", "x"))
            .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void drainReturnsAndClearsPending() {
    var harness = newHarness(100, 3600);

    harness.buffer().stage("dsl/A.java", "alpha");
    harness.buffer().stage("dsl/B.java", "beta");

    Map<String, String> drained = harness.buffer().drain();

    assertThat(drained)
            .containsEntry("dsl/A.java", "alpha")
            .containsEntry("dsl/B.java", "beta")
            .hasSize(2);
    assertThat(harness.buffer().pendingCount()).isZero();
    assertThat(harness.buffer().get("dsl/A.java")).isNull();
  }

  @Test
  void drainIsIdempotent() {
    var harness = newHarness(100, 3600);

    harness.buffer().stage("dsl/A.java", "alpha");

    Map<String, String> first = harness.buffer().drain();
    Map<String, String> second = harness.buffer().drain();

    assertThat(first).hasSize(1);
    assertThat(second).isEmpty();
  }

  @Test
  void stageOverridesPreviousValueForSameKey() {
    var harness = newHarness(100, 3600);

    harness.buffer().stage("dsl/A.java", "v1");
    harness.buffer().stage("dsl/A.java", "v2");

    assertThat(harness.buffer().get("dsl/A.java")).isEqualTo("v2");
    assertThat(harness.buffer().pendingCount()).isEqualTo(1);
  }

  @Test
  void capacityEvictsOldestEntries() {
    var harness = newHarness(2, 3600);

    harness.buffer().stage("dsl/A.java", "a");
    harness.buffer().stage("dsl/B.java", "b");
    harness.buffer().stage("dsl/C.java", "c");
    harness.cache().cleanUp();

    // Caffeine maximumSize uses W-TinyLFU; with size 2 we expect at most 2 entries
    // and the freshly staged one to survive.
    assertThat(harness.buffer().pendingCount()).isLessThanOrEqualTo(2);
    assertThat(harness.buffer().get("dsl/C.java")).isEqualTo("c");
  }

  @Test
  void expireAfterWriteEvictsStaleEntries() {
    var ticker = new MutableTicker();
    // TTL 1 second; expiry computed via ticker.
    var harness = newHarness(100, 1, ticker);

    harness.buffer().stage("dsl/A.java", "alpha");
    assertThat(harness.buffer().get("dsl/A.java")).isEqualTo("alpha");

    ticker.advance(Duration.ofSeconds(2));
    harness.cache().cleanUp();

    assertThat(harness.buffer().pendingCount()).isZero();
    assertThat(harness.buffer().get("dsl/A.java")).isNull();
  }

  private static Harness newHarness(int maxEntries, long expireAfterWriteSeconds) {
    return newHarness(maxEntries, expireAfterWriteSeconds, Ticker.systemTicker());
  }

  private static Harness newHarness(int maxEntries, long expireAfterWriteSeconds, Ticker ticker) {
    var properties = new DslBuilderProperties(
            null,
            Duration.ofMinutes(10),
            Duration.ofHours(1),
            null,
            "0.0.1-SNAPSHOT",
            "1.27.0",
            "4.0.4",
            "v1",
            java.util.List.of("clean", "build"),
            java.util.List.of("dsl", "models"),
            "project/templates",
            null,
            null,
            null,
            null,
            new DslBuilderProperties.FileBuffer(maxEntries, expireAfterWriteSeconds),
            new DslBuilderProperties.Bundles(1, 200),
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null);
    Cache<String, String> cache = BuilderServiceConfiguration.pendingCache(properties, ticker);
    return new Harness(new FileBuffer(cache), cache);
  }

  private record Harness(FileBuffer buffer, Cache<String, String> cache) {
  }

  private static final class MutableTicker implements Ticker {
    private final AtomicLong nanos = new AtomicLong();

    @Override
    public long read() {
      return nanos.get();
    }

    void advance(Duration duration) {
      nanos.addAndGet(duration.toNanos());
    }
  }
}
