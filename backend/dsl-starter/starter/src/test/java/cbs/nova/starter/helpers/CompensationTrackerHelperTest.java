package cbs.nova.starter.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.config.ContextFactory;
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
}
