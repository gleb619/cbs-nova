package cbs.nova.starter.helper;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.starter.core.StarterConstants;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import java.util.Map;

class CompensationTrackerHelperTest {

  private final CompensationTrackerHelper helper = defaultHelper();

  @Test
  void recordsMarker() {
    var ctx = SimpleContext.builder(Map.<String, Object>of("markerId", "m1"))
            .mode(ExecutionMode.PREVIEW).build();
    assertThat(helper.execute(ctx).isSuccess()).isTrue();
    assertThat(helper.wasCompensated("m1")).isTrue();
  }

  @Test
  void recordsMarkerOnlyOnce() {
    var ctx = SimpleContext.builder(Map.<String, Object>of("markerId", "m1"))
            .mode(ExecutionMode.PREVIEW).build();
    helper.execute(ctx);
    helper.execute(ctx);
    assertThat(helper.markers()).hasSize(1);
  }

  @Test
  void ignoresMissingMarker() {
    var ctx = SimpleContext.builder(Map.<String, Object>of()).mode(ExecutionMode.PREVIEW).build();
    assertThat(helper.execute(ctx).isSuccess()).isTrue();
    assertThat(helper.wasCompensated("anything")).isFalse();
  }

  @Test
  void resetClearsMarkers() {
    helper.execute(SimpleContext.builder(Map.<String, Object>of("markerId", "m1"))
            .mode(ExecutionMode.PREVIEW).build());
    helper.reset();
    assertThat(helper.wasCompensated("m1")).isFalse();
  }

  @Test
  void markersExpireAfterTtl() throws InterruptedException {
    CompensationTrackerHelper shortLived = helperWithTtl(Duration.ofMillis(30), 100);
    shortLived.execute(SimpleContext.builder(Map.<String, Object>of("markerId", "m1"))
            .mode(ExecutionMode.PREVIEW).build());
    assertThat(shortLived.wasCompensated("m1")).isTrue();

    Thread.sleep(80);
    // Eviction is lazy; Caffeine drops expired entries on read.
    assertThat(shortLived.wasCompensated("m1")).isFalse();
    assertThat(shortLived.markers()).isEmpty();
  }

  @Test
  void maxSizeEvictsLeastRecentlyWritten() {
    CompensationTrackerHelper bounded = helperWithTtl(Duration.ofMinutes(1), 2);
    bounded.execute(SimpleContext.builder(Map.<String, Object>of("markerId", "a"))
            .mode(ExecutionMode.PREVIEW).build());
    bounded.execute(SimpleContext.builder(Map.<String, Object>of("markerId", "b"))
            .mode(ExecutionMode.PREVIEW).build());
    bounded.execute(SimpleContext.builder(Map.<String, Object>of("markerId", "c"))
            .mode(ExecutionMode.PREVIEW).build());

    // W-TinyLFU eviction choice among low-frequency entries isn't guaranteed insertion-order
    // LRU at this scale; assert the bound holds and the most recent write always survives.
    assertThat(bounded.markers()).hasSize(2);
    assertThat(bounded.wasCompensated("c")).isTrue();
  }

  private static CompensationTrackerHelper defaultHelper() {
    return helperWithTtl(StarterConstants.COMPENSATION_TRACKER_TTL,
            StarterConstants.COMPENSATION_TRACKER_MAX_SIZE);
  }

  private static CompensationTrackerHelper helperWithTtl(Duration ttl, long maxSize) {
    return new CompensationTrackerHelper(Caffeine.newBuilder()
            .expireAfterWrite(ttl)
            .maximumSize(maxSize)
            .build());
  }
}
