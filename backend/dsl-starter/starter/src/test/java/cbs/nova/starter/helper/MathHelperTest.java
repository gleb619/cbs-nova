package cbs.nova.starter.helper;

import static cbs.nova.dsl.config.Constants.EXPLAIN_BUDGET_CHARS_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.MathIn;
import cbs.nova.starter.helper.model.MathOut;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MathHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final MathHelper helper = new MathHelper();

  @Test
  void sumSimple() {
    Result<MathOut> result = execute(
            new MathIn("sum", List.<Number>of(1, 2, 3), null, null, null, null, null));
    assertThat(result.isSuccess()).isTrue();
    assertThat((Double) result.value().result()).isEqualTo(6.0);
  }

  @Test
  void sumEmptyFails() {
    Result<MathOut> result = execute(
            new MathIn("sum", new ArrayList<Number>(), null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void meanOneToFive() {
    Result<MathOut> result = execute(
            new MathIn("mean", List.<Number>of(1, 2, 3, 4, 5), null, null, null, null, null));
    assertThat((Double) result.value().result()).isEqualTo(3.0);
  }

  @Test
  void medianOddLength() {
    Result<MathOut> result = execute(
            new MathIn("median", List.<Number>of(3, 1, 2), null, null, null, null, null));
    assertThat((Double) result.value().result()).isEqualTo(2.0);
  }

  @Test
  void medianEvenLengthAveragesMiddles() {
    Result<MathOut> result = execute(
            new MathIn("median", List.<Number>of(1, 2, 3, 4), null, null, null, null, null));
    assertThat((Double) result.value().result()).isEqualTo(2.5);
  }

  @Test
  void percentile50EqualsMedianForOddLengthList() {
    Result<MathOut> median = execute(new MathIn("median", List.<Number>of(10, 20, 30, 40, 50), null,
            null, null, null, null));
    Result<MathOut> p50 = execute(new MathIn("percentile", List.<Number>of(10, 20, 30, 40, 50),
            null, null, null, null, 50.0));
    assertThat((Double) median.value().result()).isEqualTo((Double) p50.value().result());
    assertThat((Double) p50.value().result()).isEqualTo(30.0);
  }

  @Test
  void percentile99ForOneHundredSequentialValues() {
    List<Number> numbers = new ArrayList<>();
    for (int i = 1; i <= 100; i++) {
      numbers.add(i);
    }
    Result<MathOut> result = execute(
            new MathIn("percentile", numbers, null, null, null, null, 99.0));
    // Linear interpolation (Hyndman-Fan type 7): index = 99/100 * (100 - 1) = 98.01,
    // so value = a[98] + (a[99] - a[98]) * (98.01 - 98) = 99 + 1 * 0.01 = 99.01.
    assertThat((Double) result.value().result()).isCloseTo(99.01, within(1e-6));
  }

  @Test
  void percentileOutOfRangeFails() {
    Result<MathOut> result = execute(
            new MathIn("percentile", List.<Number>of(1, 2, 3), null, null, null, null, 150.0));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void stddevKnownVector() {
    Result<MathOut> result = execute(
            new MathIn(
                    "stddev",
                    List.<Number>of(2, 4, 4, 4, 5, 5, 7, 9),
                    null,
                    null,
                    null,
                    null,
                    null));
    assertThat((Double) result.value().result()).isCloseTo(2.138, within(0.001));
  }

  @Test
  void stddevSingleElementFails() {
    Result<MathOut> result = execute(
            new MathIn("stddev", List.<Number>of(5), null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void clampInRangeReturnsValue() {
    Result<MathOut> result = execute(new MathIn("clamp", null, 5, 0, 10, null, null));
    assertThat((Double) result.value().result()).isEqualTo(5.0);
  }

  @Test
  void clampBelowMinReturnsMin() {
    Result<MathOut> result = execute(new MathIn("clamp", null, -5, 0, 10, null, null));
    assertThat((Double) result.value().result()).isEqualTo(0.0);
  }

  @Test
  void clampAboveMaxReturnsMax() {
    Result<MathOut> result = execute(new MathIn("clamp", null, 15, 0, 10, null, null));
    assertThat((Double) result.value().result()).isEqualTo(10.0);
  }

  @Test
  void clampMinGreaterThanMaxFails() {
    Result<MathOut> result = execute(new MathIn("clamp", null, 5, 10, 0, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void roundThreePointOneFourOneFiveToTwoPlaces() {
    Result<MathOut> result = execute(new MathIn("round", null, 3.14159, null, null, 2, null));
    assertThat((Double) result.value().result()).isEqualTo(3.14);
  }

  @Test
  void roundHalfUpThreePointFive() {
    Result<MathOut> result = execute(new MathIn("round", null, 3.5, null, null, 0, null));
    assertThat((Double) result.value().result()).isEqualTo(4.0);
  }

  @Test
  void roundHalfUpTwoPointFive() {
    Result<MathOut> result = execute(new MathIn("round", null, 2.5, null, null, 0, null));
    assertThat((Double) result.value().result()).isEqualTo(3.0);
  }

  @Test
  void absPositive() {
    Result<MathOut> result = execute(new MathIn("abs", null, 5, null, null, null, null));
    assertThat((Double) result.value().result()).isEqualTo(5.0);
  }

  @Test
  void absNegative() {
    Result<MathOut> result = execute(new MathIn("abs", null, -5, null, null, null, null));
    assertThat((Double) result.value().result()).isEqualTo(5.0);
  }

  @Test
  void floorThreePointSeven() {
    Result<MathOut> result = execute(new MathIn("floor", null, 3.7, null, null, null, null));
    assertThat((Long) result.value().result()).isEqualTo(3L);
  }

  @Test
  void ceilThreePointTwo() {
    Result<MathOut> result = execute(new MathIn("ceil", null, 3.2, null, null, null, null));
    assertThat((Long) result.value().result()).isEqualTo(4L);
  }

  @Test
  void unknownModeFails() {
    Result<MathOut> result = execute(new MathIn("frobnicate", null, null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause())
            .hasMessage(
                    "math.mode must be one of sum, min, max, mean, median, percentile,"
                            + " stddev, clamp, round, abs, floor, ceil, was: frobnicate");
  }

  @Test
  void nonNumberElementFailsMentioningIndex() {
    @SuppressWarnings({"unchecked", "rawtypes"})
    List<Number> numbers = (List<Number>) (List<?>) (List) List.of(1, "oops", 3);
    Result<MathOut> result = execute(new MathIn("sum", numbers, null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("index 1");
  }

  private Result<MathOut> execute(MathIn input) {
    var ctx = contextFactory.of(input, ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }

  @Test
  void explainPercentileDescribesInterpolationAndArgs() {
    var ctx = contextFactory.of(
            new MathIn("percentile", List.<Number>of(10, 20, 30), null, null, null, null, 95.0),
            ExecutionMode.EXPLAIN);

    var report = helper.explain(ctx);

    assertThat(report.name()).isEqualTo("math");
    assertThat(report.description())
            .contains("linear interpolation")
            .contains("Hyndman-Fan type 7")
            .contains("p=95.0");
    assertThat(report.mermaid()).contains("graph TD", "percentile");
  }

  @Test
  void explainDiffersBetweenModes() {
    var sumCtx = contextFactory.of(
            new MathIn("sum", List.<Number>of(1, 2), null, null, null, null, null),
            ExecutionMode.EXPLAIN);
    var stddevCtx = contextFactory.of(
            new MathIn("stddev", List.<Number>of(1, 2), null, null, null, null, null),
            ExecutionMode.EXPLAIN);

    var sum = helper.explain(sumCtx);
    var stddev = helper.explain(stddevCtx);

    assertThat(sum.description()).contains("double sum");
    assertThat(stddev.description()).contains("sample standard deviation");
    assertThat(sum.description()).isNotEqualTo(stddev.description());
    assertThat(sum.mermaid()).contains("sum");
    assertThat(stddev.mermaid()).contains("stddev");
  }

  @Test
  void explainReflectsClampAndRoundArgs() {
    var clampCtx = contextFactory.of(
            new MathIn("clamp", null, 5, 0, 10, null, null), ExecutionMode.EXPLAIN);
    var roundCtx = contextFactory.of(
            new MathIn("round", null, 3.14159, null, null, 2, null), ExecutionMode.EXPLAIN);

    assertThat(helper.explain(clampCtx).description())
            .contains("min=0", "max=10");
    assertThat(helper.explain(roundCtx).description())
            .contains("scale=2");
  }

  @Test
  void explainUnknownModeMentionsExpectedModes() {
    var ctx = contextFactory.of(
            new MathIn("frobnicate", null, null, null, null, null, null), ExecutionMode.EXPLAIN);

    var report = helper.explain(ctx);

    assertThat(report.description()).contains("unknown mode `frobnicate`", "sum");
  }

  @Test
  void explainDoesNotTruncateToBudget() {
    var ctx = contextFactory.of(
            new MathIn("percentile", List.<Number>of(1, 2, 3), null, null, null, null, 99.0),
            ExecutionMode.EXPLAIN).withMetadata(EXPLAIN_BUDGET_CHARS_KEY, 50);

    var report = helper.explain(ctx);

    assertThat(report.description()).contains("percentile");
    assertThat(report.mermaid()).isNotEmpty();
    assertThat(report.description().length() + report.mermaid().length()).isGreaterThan(50);
  }
}
