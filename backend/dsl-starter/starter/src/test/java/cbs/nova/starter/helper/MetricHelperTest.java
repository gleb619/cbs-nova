package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.MetricIn;
import cbs.nova.starter.helper.model.MetricOut;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MetricHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final MeterRegistry registry = new SimpleMeterRegistry();
  private final MetricHelper helper = new MetricHelper(registry);

  @Test
  void counterNoTagsDefaultAmountIncrementsByOne() {
    MetricIn input = new MetricIn("counter", "x", null, null, null, null);

    Result<MetricOut> result = execute(input);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().emitted()).isTrue();
    assertThat(registry.find("x").counter().count()).isEqualTo(1.0);
  }

  @Test
  void counterWithTagsAndAmountFiveIncrementsByFive() {
    Map<String, String> tags = Map.of("region", "eu");
    MetricIn input = new MetricIn("counter", "x", tags, null, 5L, null);

    Result<MetricOut> result = execute(input);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().emitted()).isTrue();
    assertThat(registry.find("x").tag("region", "eu").counter().count()).isEqualTo(5.0);
  }

  @Test
  void gaugeLastWinsWithSameNameAndTags() {
    Result<MetricOut> first = execute(new MetricIn("gauge", "g", null, 3.0, null, null));
    Result<MetricOut> second = execute(new MetricIn("gauge", "g", null, 7.0, null, null));

    assertThat(first.isSuccess()).isTrue();
    assertThat(second.isSuccess()).isTrue();
    assertThat(registry.find("g").gauge().value()).isEqualTo(7.0);
  }

  @Test
  void timerRecordsExactDurationOnSimpleMeterRegistry() {
    Result<MetricOut> result = execute(new MetricIn("timer", "t", null, null, null, 250L));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().emitted()).isTrue();
    assertThat(registry.find("t").timer().totalTime(TimeUnit.MILLISECONDS))
            .isGreaterThanOrEqualTo(250.0);
  }

  @Test
  void distributionSummaryRecordsValue() {
    Result<MetricOut> result = execute(new MetricIn("summary", "s", null, 42.0, null, null));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().emitted()).isTrue();
    assertThat(registry.find("s").summary().totalAmount()).isEqualTo(42.0);
  }

  @Test
  void nullRegistryReturnsEmittedFalseButStillValidates() {
    MetricHelper noRegistryHelper = new MetricHelper((MeterRegistry) null);
    MetricIn valid = new MetricIn("counter", "x", null, null, 1L, null);

    Result<MetricOut> result = noRegistryHelper.execute(
            contextFactory.of(valid, ExecutionMode.PREVIEW));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().emitted()).isFalse();
  }

  @Test
  void nullRegistryStillRunsValidationBeforeNoOp() {
    MetricHelper noRegistryHelper = new MetricHelper((MeterRegistry) null);
    MetricIn blankName = new MetricIn("counter", "  ", null, null, null, null);

    Result<MetricOut> result = noRegistryHelper.execute(
            contextFactory.of(blankName, ExecutionMode.PREVIEW));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("metric.name is required");
  }

  @Test
  void blankNameFails() {
    Result<MetricOut> result = execute(new MetricIn("counter", "", null, null, null, null));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("metric.name is required");
  }

  @Test
  void unknownTypeFails() {
    Result<MetricOut> result = execute(new MetricIn("histogram", "x", null, null, null, null));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause())
            .hasMessageContaining("metric.type must be one of: counter, gauge, timer, summary");
  }

  @Test
  void negativeCounterAmountFails() {
    Result<MetricOut> result = execute(new MetricIn("counter", "x", null, null, -1L, null));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("metric.amount must be >= 0");
  }

  @Test
  void negativeTimerDurationFails() {
    Result<MetricOut> result = execute(new MetricIn("timer", "x", null, null, null, -1L));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("metric.durationMs must be >= 0");
  }

  @Test
  void gaugeNullValueFails() {
    Result<MetricOut> result = execute(new MetricIn("gauge", "x", null, null, null, null));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("metric.value is required");
  }

  @Test
  void nullTagKeyFails() {
    HashMap<String, String> tags = new HashMap<>();
    tags.put(null, "v");
    Result<MetricOut> result = execute(new MetricIn("counter", "x", tags, null, 1L, null));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("metric.tags key must not be null");
  }

  private Result<MetricOut> execute(MetricIn input) {
    return helper.execute(contextFactory.of(input, ExecutionMode.PREVIEW));
  }
}
