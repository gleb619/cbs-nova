package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.config.ContextFactory;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import java.util.Map;

class CompensationTrackerHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final CompensationTrackerHelper helper = new CompensationTrackerHelper();

  @Test
  void recordsMarker() {
    var ctx = contextFactory.of(
            Map.<String, Object>of("markerId", "m1"), ExecutionMode.PREVIEW);
    assertThat(helper.execute(ctx).isSuccess()).isTrue();
    assertThat(helper.wasCompensated("m1")).isTrue();
  }

  @Test
  void recordsMarkerOnlyOnce() {
    var ctx = contextFactory.of(
            Map.<String, Object>of("markerId", "m1"), ExecutionMode.PREVIEW);
    helper.execute(ctx);
    helper.execute(ctx);
    assertThat(helper.markers()).hasSize(1);
  }

  @Test
  void ignoresMissingMarker() {
    var ctx = contextFactory.of(Map.<String, Object>of(), ExecutionMode.PREVIEW);
    assertThat(helper.execute(ctx).isSuccess()).isTrue();
    assertThat(helper.wasCompensated("anything")).isFalse();
  }

  @Test
  void resetClearsMarkers() {
    helper.execute(contextFactory.of(
            Map.<String, Object>of("markerId", "m1"), ExecutionMode.PREVIEW));
    helper.reset();
    assertThat(helper.wasCompensated("m1")).isFalse();
  }

  @Test
  void markersExpireAfterTtl() throws InterruptedException {
    CompensationTrackerHelper shortLived = new CompensationTrackerHelper(Duration.ofMillis(30),
            100);
    shortLived.execute(contextFactory.of(
            Map.<String, Object>of("markerId", "m1"), ExecutionMode.PREVIEW));
    assertThat(shortLived.wasCompensated("m1")).isTrue();

    Thread.sleep(80);
    // Eviction is lazy; Caffeine drops expired entries on read.
    assertThat(shortLived.wasCompensated("m1")).isFalse();
    assertThat(shortLived.markers()).isEmpty();
  }

  @Test
  void maxSizeEvictsLeastRecentlyWritten() {
    CompensationTrackerHelper bounded = new CompensationTrackerHelper(Duration.ofMinutes(1), 2);
    bounded.execute(contextFactory.of(
            Map.<String, Object>of("markerId", "a"), ExecutionMode.PREVIEW));
    bounded.execute(contextFactory.of(
            Map.<String, Object>of("markerId", "b"), ExecutionMode.PREVIEW));
    bounded.execute(contextFactory.of(
            Map.<String, Object>of("markerId", "c"), ExecutionMode.PREVIEW));

    // LRU/W-TinyLFU evicts the eldest insert when bounded.
    assertThat(bounded.markers()).hasSize(2);
    assertThat(bounded.wasCompensated("c")).isTrue();
    assertThat(bounded.wasCompensated("b")).isTrue();
  }
}
