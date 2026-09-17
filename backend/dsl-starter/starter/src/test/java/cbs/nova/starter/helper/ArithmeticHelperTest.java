package cbs.nova.starter.helper;

import cbs.nova.dsl.model.SimpleContext;
import static cbs.nova.dsl.config.Constants.EXPLAIN_BUDGET_CHARS_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.starter.helper.model.ArithmeticIn;
import cbs.nova.starter.helper.model.ArithmeticOut;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArithmeticHelperTest {

  private final ArithmeticHelper helper = new ArithmeticHelper();

  @Test
  void addsValues() {
    var ctx = SimpleContext.builder(op(null, List.of(1.0, 2.0, 3.0))).mode(ExecutionMode.PREVIEW)
            .build();
    Result<ArithmeticOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().sum()).isEqualByComparingTo(BigDecimal.valueOf(6));
  }

  @Test
  void subtractsValues() {
    var ctx = SimpleContext.builder(op("SUBTRACT", List.of(10, 3, 2))).mode(ExecutionMode.PREVIEW)
            .build();
    assertThat(helper.execute(ctx).value().sum()).isEqualByComparingTo(BigDecimal.valueOf(5));
  }

  @Test
  void multipliesValues() {
    var ctx = SimpleContext.builder(op("MULTIPLY", List.of(2, 3, 4))).mode(ExecutionMode.PREVIEW)
            .build();
    assertThat(helper.execute(ctx).value().sum()).isEqualByComparingTo(BigDecimal.valueOf(24));
  }

  @Test
  void dividesValues() {
    var ctx = SimpleContext.builder(op("DIVIDE", List.of(100, 4, 5))).mode(ExecutionMode.PREVIEW)
            .build();
    assertThat(helper.execute(ctx).value().sum()).isEqualByComparingTo(BigDecimal.valueOf(5));
  }

  @Test
  void computesMinAndMax() {
    var min = helper.execute(
            SimpleContext.builder(op("MIN", List.of(3, 1, 2))).mode(ExecutionMode.PREVIEW).build());
    assertThat(min.value().sum()).isEqualByComparingTo(BigDecimal.ONE);

    var max = helper.execute(
            SimpleContext.builder(op("MAX", List.of(3, 1, 2))).mode(ExecutionMode.PREVIEW).build());
    assertThat(max.value().sum()).isEqualByComparingTo(BigDecimal.valueOf(3));
  }

  @Test
  void returnsZeroForEmpty() {
    var ctx = SimpleContext.builder(op(null, List.of())).mode(ExecutionMode.PREVIEW).build();
    assertThat(helper.execute(ctx).value().sum()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void returnsZeroForNullList() {
    var ctx = SimpleContext.builder(op(null, null)).mode(ExecutionMode.PREVIEW).build();
    assertThat(helper.execute(ctx).value().sum()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void sumSimple() {
    Result<ArithmeticOut> result = execute(
            math("sum", List.<Number>of(1, 2, 3), null, null, null, null, null));
    assertThat(result.isSuccess()).isTrue();
    assertThat((Double) result.value().result()).isEqualTo(6.0);
  }

  @Test
  void sumEmptyFails() {
    Result<ArithmeticOut> result = execute(
            math("sum", new ArrayList<Number>(), null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void meanOneToFive() {
    Result<ArithmeticOut> result = execute(
            math("mean", List.<Number>of(1, 2, 3, 4, 5), null, null, null, null, null));
    assertThat((Double) result.value().result()).isEqualTo(3.0);
  }

  @Test
  void medianOddLength() {
    Result<ArithmeticOut> result = execute(
            math("median", List.<Number>of(3, 1, 2), null, null, null, null, null));
    assertThat((Double) result.value().result()).isEqualTo(2.0);
  }

  @Test
  void medianEvenLengthAveragesMiddles() {
    Result<ArithmeticOut> result = execute(
            math("median", List.<Number>of(1, 2, 3, 4), null, null, null, null, null));
    assertThat((Double) result.value().result()).isEqualTo(2.5);
  }

  @Test
  void percentile50EqualsMedianForOddLengthList() {
    Result<ArithmeticOut> median = execute(math("median", List.<Number>of(10, 20, 30, 40, 50), null,
            null, null, null, null));
    Result<ArithmeticOut> p50 = execute(math("percentile", List.<Number>of(10, 20, 30, 40, 50),
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
    Result<ArithmeticOut> result = execute(
            math("percentile", numbers, null, null, null, null, 99.0));
    assertThat((Double) result.value().result()).isCloseTo(99.01, within(1e-6));
  }

  @Test
  void percentileOutOfRangeFails() {
    Result<ArithmeticOut> result = execute(
            math("percentile", List.<Number>of(1, 2, 3), null, null, null, null, 150.0));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void stddevKnownVector() {
    Result<ArithmeticOut> result = execute(
            math("stddev",
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
    Result<ArithmeticOut> result = execute(
            math("stddev", List.<Number>of(5), null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void clampInRangeReturnsValue() {
    Result<ArithmeticOut> result = execute(math("clamp", null, 5, 0, 10, null, null));
    assertThat((Double) result.value().result()).isEqualTo(5.0);
  }

  @Test
  void clampBelowMinReturnsMin() {
    Result<ArithmeticOut> result = execute(math("clamp", null, -5, 0, 10, null, null));
    assertThat((Double) result.value().result()).isEqualTo(0.0);
  }

  @Test
  void clampAboveMaxReturnsMax() {
    Result<ArithmeticOut> result = execute(math("clamp", null, 15, 0, 10, null, null));
    assertThat((Double) result.value().result()).isEqualTo(10.0);
  }

  @Test
  void clampMinGreaterThanMaxFails() {
    Result<ArithmeticOut> result = execute(math("clamp", null, 5, 10, 0, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void roundThreePointOneFourOneFiveToTwoPlaces() {
    Result<ArithmeticOut> result = execute(math("round", null, 3.14159, null, null, 2, null));
    assertThat((Double) result.value().result()).isEqualTo(3.14);
  }

  @Test
  void roundHalfUpThreePointFive() {
    Result<ArithmeticOut> result = execute(math("round", null, 3.5, null, null, 0, null));
    assertThat((Double) result.value().result()).isEqualTo(4.0);
  }

  @Test
  void roundHalfUpTwoPointFive() {
    Result<ArithmeticOut> result = execute(math("round", null, 2.5, null, null, 0, null));
    assertThat((Double) result.value().result()).isEqualTo(3.0);
  }

  @Test
  void absPositive() {
    Result<ArithmeticOut> result = execute(math("abs", null, 5, null, null, null, null));
    assertThat((Double) result.value().result()).isEqualTo(5.0);
  }

  @Test
  void absNegative() {
    Result<ArithmeticOut> result = execute(math("abs", null, -5, null, null, null, null));
    assertThat((Double) result.value().result()).isEqualTo(5.0);
  }

  @Test
  void floorThreePointSeven() {
    Result<ArithmeticOut> result = execute(math("floor", null, 3.7, null, null, null, null));
    assertThat((Long) result.value().result()).isEqualTo(3L);
  }

  @Test
  void ceilThreePointTwo() {
    Result<ArithmeticOut> result = execute(math("ceil", null, 3.2, null, null, null, null));
    assertThat((Long) result.value().result()).isEqualTo(4L);
  }

  @Test
  void unknownModeFails() {
    Result<ArithmeticOut> result = execute(math("frobnicate", null, null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause())
            .hasMessage(
                    "arithmetic.mode must be one of sum, min, max, mean, median, percentile,"
                            + " stddev, clamp, round, abs, floor, ceil, was: frobnicate");
  }

  @Test
  void nonNumberElementFailsMentioningIndex() {
    @SuppressWarnings({"unchecked", "rawtypes"})
    List<Number> numbers = (List<Number>) (List<?>) (List) List.of(1, "oops", 3);
    Result<ArithmeticOut> result = execute(math("sum", numbers, null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("index 1");
  }

  @Test
  void explainPercentileDescribesInterpolationAndArgs() {
    var ctx = SimpleContext
            .builder(math("percentile", List.<Number>of(10, 20, 30), null, null, null, null, 95.0))
            .mode(ExecutionMode.EXPLAIN).build();

    var report = helper.explain(ctx);

    assertThat(report.name()).isEqualTo("arithmetic");
    assertThat(report.description())
            .contains("linear interpolation")
            .contains("Hyndman-Fan type 7")
            .contains("p=95.0");
    assertThat(report.mermaid()).contains("graph TD", "percentile");
  }

  @Test
  void explainDiffersBetweenModes() {
    var sumCtx = SimpleContext
            .builder(math("sum", List.<Number>of(1, 2), null, null, null, null, null))
            .mode(ExecutionMode.EXPLAIN).build();
    var stddevCtx = SimpleContext
            .builder(math("stddev", List.<Number>of(1, 2), null, null, null, null, null))
            .mode(ExecutionMode.EXPLAIN).build();

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
    var clampCtx = SimpleContext.builder(math("clamp", null, 5, 0, 10, null, null))
            .mode(ExecutionMode.EXPLAIN).build();
    var roundCtx = SimpleContext.builder(math("round", null, 3.14159, null, null, 2, null))
            .mode(ExecutionMode.EXPLAIN).build();

    assertThat(helper.explain(clampCtx).description())
            .contains("min=0", "max=10");
    assertThat(helper.explain(roundCtx).description())
            .contains("scale=2");
  }

  @Test
  void explainUnknownModeMentionsExpectedModes() {
    var ctx = SimpleContext.builder(math("frobnicate", null, null, null, null, null, null))
            .mode(ExecutionMode.EXPLAIN).build();

    var report = helper.explain(ctx);

    assertThat(report.description()).contains("unknown mode `frobnicate`", "sum");
  }

  @Test
  void explainDoesNotTruncateToBudget() {
    var ctx = SimpleContext
            .builder(math("percentile", List.<Number>of(1, 2, 3), null, null, null, null, 99.0))
            .mode(ExecutionMode.EXPLAIN).build().withMetadata(EXPLAIN_BUDGET_CHARS_KEY, 50);

    var report = helper.explain(ctx);

    assertThat(report.description()).contains("percentile");
    assertThat(report.mermaid()).isNotEmpty();
    assertThat(report.description().length() + report.mermaid().length()).isGreaterThan(50);
  }

  private Result<ArithmeticOut> execute(ArithmeticIn input) {
    var ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();
    return helper.execute(ctx);
  }

  private ArithmeticIn op(String operation, List<Number> values) {
    return new ArithmeticIn(null, operation, null, values, null, null, null, null, null);
  }

  private ArithmeticIn math(
          String mode,
          List<Number> numbers,
          Number value,
          Number min,
          Number max,
          Integer scale,
          Double p) {
    return new ArithmeticIn(mode, null, numbers, null, value, min, max, scale, p);
  }
}
