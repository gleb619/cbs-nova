package cbs.nova.starter.helper;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.starter.helper.model.ParseDurationIn;
import cbs.nova.starter.helper.model.ParseDurationOut;
import org.junit.jupiter.api.Test;

class ParseDurationHelperTest {

  private final ParseDurationHelper helper = new ParseDurationHelper();

  // --- ISO forms ---------------------------------------------------------

  @Test
  void isoHoursAndMinutes() {
    ParseDurationOut out = parse("PT1H30M");
    assertThat(out.millis()).isEqualTo(5_400_000L);
    assertThat(out.seconds()).isEqualTo(5_400L);
    assertThat(out.iso()).isEqualTo("PT1H30M");
  }

  @Test
  void isoDaysAndHours() {
    ParseDurationOut out = parse("P2DT3H");
    assertThat(out.millis()).isEqualTo(183_600_000L);
    assertThat(out.seconds()).isEqualTo(183_600L);
    assertThat(out.iso()).isEqualTo("PT51H");
  }

  @Test
  void isoBareDaysNormalizesToHours() {
    ParseDurationOut out = parse("P2D");
    assertThat(out.millis()).isEqualTo(172_800_000L);
    assertThat(out.seconds()).isEqualTo(172_800L);
    assertThat(out.iso()).isEqualTo("PT48H");
  }

  @Test
  void isoFractionalSeconds() {
    ParseDurationOut out = parse("PT0.5S");
    assertThat(out.millis()).isEqualTo(500L);
    assertThat(out.seconds()).isEqualTo(0L);
    assertThat(out.iso()).isEqualTo("PT0.5S");
  }

  @Test
  void isoSecondsOnly() {
    ParseDurationOut out = parse("PT45S");
    assertThat(out.millis()).isEqualTo(45_000L);
    assertThat(out.seconds()).isEqualTo(45L);
    assertThat(out.iso()).isEqualTo("PT45S");
  }

  @Test
  void isoZero() {
    ParseDurationOut out = parse("PT0S");
    assertThat(out.millis()).isEqualTo(0L);
    assertThat(out.seconds()).isEqualTo(0L);
    assertThat(out.iso()).isEqualTo("PT0S");
  }

  @Test
  void isoLowercaseLettersAccepted() {
    ParseDurationOut out = parse("pt1h30m");
    assertThat(out.millis()).isEqualTo(5_400_000L);
    assertThat(out.seconds()).isEqualTo(5_400L);
    assertThat(out.iso()).isEqualTo("PT1H30M");
  }

  // --- shorthand units ---------------------------------------------------

  @Test
  void shorthandDays() {
    ParseDurationOut out = parse("2d");
    assertThat(out.millis()).isEqualTo(172_800_000L);
    assertThat(out.seconds()).isEqualTo(172_800L);
    assertThat(out.iso()).isEqualTo("PT48H");
  }

  @Test
  void shorthandHours() {
    ParseDurationOut out = parse("3h");
    assertThat(out.millis()).isEqualTo(10_800_000L);
    assertThat(out.seconds()).isEqualTo(10_800L);
    assertThat(out.iso()).isEqualTo("PT3H");
  }

  @Test
  void shorthandMinutes() {
    ParseDurationOut out = parse("90m");
    assertThat(out.millis()).isEqualTo(5_400_000L);
    assertThat(out.seconds()).isEqualTo(5_400L);
    assertThat(out.iso()).isEqualTo("PT1H30M");
  }

  @Test
  void shorthandSeconds() {
    ParseDurationOut out = parse("45s");
    assertThat(out.millis()).isEqualTo(45_000L);
    assertThat(out.seconds()).isEqualTo(45L);
    assertThat(out.iso()).isEqualTo("PT45S");
  }

  @Test
  void shorthandMillis() {
    ParseDurationOut out = parse("250ms");
    assertThat(out.millis()).isEqualTo(250L);
    assertThat(out.seconds()).isEqualTo(0L);
    assertThat(out.iso()).isEqualTo("PT0.25S");
  }

  // --- compound shorthand ------------------------------------------------

  @Test
  void compoundHoursAndMinutes() {
    ParseDurationOut out = parse("1h30m");
    assertThat(out.millis()).isEqualTo(5_400_000L);
    assertThat(out.seconds()).isEqualTo(5_400L);
    assertThat(out.iso()).isEqualTo("PT1H30M");
  }

  @Test
  void compoundDaysAndHours() {
    ParseDurationOut out = parse("2d12h");
    assertThat(out.millis()).isEqualTo(216_000_000L);
    assertThat(out.seconds()).isEqualTo(216_000L);
    assertThat(out.iso()).isEqualTo("PT60H");
  }

  @Test
  void compoundAllUnits() {
    ParseDurationOut out = parse("1d2h3m4s5ms");
    assertThat(out.millis()).isEqualTo(
            86_400_000L + 7_200_000L + 180_000L + 4_000L + 5L);
    assertThat(out.seconds()).isEqualTo(93_784L);
    assertThat(out.iso()).isEqualTo("PT26H3M4.005S");
  }

  // --- whitespace / case-insensitivity ------------------------------------

  @Test
  void shorthandWhitespaceBetweenSegments() {
    ParseDurationOut out = parse("  1h   30m  ");
    assertThat(out.millis()).isEqualTo(5_400_000L);
    assertThat(out.iso()).isEqualTo("PT1H30M");
  }

  @Test
  void shorthandUppercaseUnits() {
    ParseDurationOut out = parse("1H30M");
    assertThat(out.millis()).isEqualTo(5_400_000L);
    assertThat(out.iso()).isEqualTo("PT1H30M");
  }

  // --- failures ----------------------------------------------------------

  @Test
  void nullValueFails() {
    Result<ParseDurationOut> result = execute(new ParseDurationIn(null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void blankValueFails() {
    Result<ParseDurationOut> result = execute(new ParseDurationIn("   "));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void bareNumberFails() {
    Result<ParseDurationOut> result = execute(new ParseDurationIn("5"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void garbageTextFails() {
    Result<ParseDurationOut> result = execute(new ParseDurationIn("not-a-duration"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unknownUnitFails() {
    Result<ParseDurationOut> result = execute(new ParseDurationIn("5x"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void trailingUnknownUnitFails() {
    Result<ParseDurationOut> result = execute(new ParseDurationIn("1h30x"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void negativeShorthandLeadingMinusFails() {
    Result<ParseDurationOut> result = execute(new ParseDurationIn("-1h"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("signed durations are not supported");
  }

  @Test
  void negativeIsoLeadingMinusFails() {
    Result<ParseDurationOut> result = execute(new ParseDurationIn("-PT1H"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("signed durations are not supported");
  }

  @Test
  void negativeIsoEmbeddedSignFails() {
    Result<ParseDurationOut> result = execute(new ParseDurationIn("P-1D"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void overflowShorthandFails() {
    // One day more than Long.MAX_VALUE milliseconds.
    Result<ParseDurationOut> result = execute(new ParseDurationIn("106751991168d"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("exceeds Long.MAX_VALUE");
  }

  @Test
  void overflowIsoFails() {
    Result<ParseDurationOut> result = execute(new ParseDurationIn("P106751991168D"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  // --- round-trip / edge cases -------------------------------------------

  @Test
  void isoRoundTrip() {
    ParseDurationOut first = parse("1h30m");
    ParseDurationOut second = parse(first.iso());
    assertThat(second.millis()).isEqualTo(first.millis());
    assertThat(second.seconds()).isEqualTo(first.seconds());
    assertThat(second.iso()).isEqualTo(first.iso());
  }

  @Test
  void largeButValidShorthand() {
    // Largest whole-day count that still fits in Long.MAX_VALUE milliseconds.
    ParseDurationOut out = parse("106751991167d");
    assertThat(out.millis()).isEqualTo(9_223_372_036_828_800_000L);
    assertThat(out.seconds()).isEqualTo(9_223_372_036_828_800L);
    assertThat(out.iso()).isEqualTo("PT2562047788008H");
  }

  @Test
  void shorthandMinutesAlwaysMeansMinutes() {
    // If "m" were interpreted as months this would be orders of magnitude larger.
    ParseDurationOut out = parse("1m");
    assertThat(out.millis()).isEqualTo(60_000L);
    assertThat(out.iso()).isEqualTo("PT1M");
  }

  // --- helpers -----------------------------------------------------------

  private ParseDurationOut parse(String value) {
    Result<ParseDurationOut> result = execute(new ParseDurationIn(value));
    assertThat(result.isSuccess()).isTrue();
    return result.value();
  }

  private Result<ParseDurationOut> execute(ParseDurationIn input) {
    var ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();
    return helper.execute(ctx);
  }
}
