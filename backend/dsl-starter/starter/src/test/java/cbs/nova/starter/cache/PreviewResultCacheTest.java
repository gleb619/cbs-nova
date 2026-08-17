package cbs.nova.starter.cache;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.CallKind;
import cbs.nova.dsl.CallNode;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.PreviewReport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class PreviewResultCacheTest {

  private final PreviewReport report = new PreviewReport(
          "Ping",
          ExecutionMode.PREVIEW,
          true,
          "pong",
          List.of("trace"),
          List.of(),
          Map.of(),
          CallNode.leaf("Ping", CallKind.PROCESS, null, "pong", true),
          List.of(),
          null,
          List.of());

  @Test
  void missOnFirstCallAndHitOnSecond() {
    var cache = new PreviewResultCache(60_000);
    var key = new PreviewCacheKey("Ping", "dsl-hash", "input-hash");

    assertThat(cache.get(key)).isNull();
    assertThat(cache.misses()).isEqualTo(1);
    assertThat(cache.hits()).isEqualTo(0);

    cache.put(key, report);

    assertThat(cache.get(key)).isEqualTo(report);
    assertThat(cache.hits()).isEqualTo(1);
    assertThat(cache.misses()).isEqualTo(1);
  }

  @Test
  void missAfterTtlExpires() throws InterruptedException {
    var cache = new PreviewResultCache(10);
    var key = new PreviewCacheKey("Ping", "dsl-hash", "input-hash");

    cache.put(key, report);
    assertThat(cache.get(key)).isEqualTo(report);

    TimeUnit.MILLISECONDS.sleep(15);

    assertThat(cache.get(key)).isNull();
    assertThat(cache.misses()).isEqualTo(1);
  }

  @Test
  void invalidateByDslHashRemovesOnlyMatchingEntries() {
    var cache = new PreviewResultCache(60_000);
    var keyA = new PreviewCacheKey("A", "hash-1", "input-1");
    var keyB = new PreviewCacheKey("B", "hash-2", "input-2");
    var keyC = new PreviewCacheKey("C", "hash-1", "input-3");

    cache.put(keyA, report);
    cache.put(keyB, report);
    cache.put(keyC, report);

    cache.invalidateByDslHash("hash-1");

    assertThat(cache.get(keyA)).isNull();
    assertThat(cache.get(keyB)).isEqualTo(report);
    assertThat(cache.get(keyC)).isNull();
  }

  @Test
  void clearRemovesAllEntries() {
    var cache = new PreviewResultCache(60_000);
    var keyA = new PreviewCacheKey("A", "hash-1", "input-1");
    var keyB = new PreviewCacheKey("B", "hash-2", "input-2");

    cache.put(keyA, report);
    cache.put(keyB, report);
    cache.clear();

    assertThat(cache.get(keyA)).isNull();
    assertThat(cache.get(keyB)).isNull();
  }

  @Test
  void concurrentGetPutOperationsAreSafe() throws InterruptedException {
    var cache = new PreviewResultCache(60_000);
    var key = new PreviewCacheKey("Ping", "dsl-hash", "input-hash");
    var threads = 8;
    var iterations = 100;
    var executor = Executors.newFixedThreadPool(threads);
    var latch = new CountDownLatch(threads * 2);
    var produced = new AtomicInteger(0);

    for (int i = 0; i < threads; i++) {
      executor.submit(() -> {
        try {
          for (int j = 0; j < iterations; j++) {
            cache.put(key, report);
          }
        } finally {
          latch.countDown();
        }
      });
      executor.submit(() -> {
        try {
          for (int j = 0; j < iterations; j++) {
            if (cache.get(key) != null) {
              produced.incrementAndGet();
            }
          }
        } finally {
          latch.countDown();
        }
      });
    }

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    executor.shutdown();

    assertThat(cache.get(key)).isNotNull();
    assertThat(cache.getStats().get("hits") + cache.getStats().get("misses"))
            .isGreaterThanOrEqualTo((long) threads * iterations);
  }

  @Test
  void statsAreAccurate() {
    var cache = new PreviewResultCache(60_000);
    var key = new PreviewCacheKey("Ping", "dsl-hash", "input-hash");

    cache.get(key);
    cache.get(key);
    cache.put(key, report);
    cache.get(key);
    cache.get(key);

    assertThat(cache.getStats()).containsExactlyInAnyOrderEntriesOf(Map.of(
            "hits", 2L,
            "misses", 2L));
  }
}
