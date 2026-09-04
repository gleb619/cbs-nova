package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.DateMathIn;
import cbs.nova.starter.helper.model.DateMathOut;
import org.junit.jupiter.api.Test;

class DateMathHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final DateMathHelper helper = new DateMathHelper();

  // ---- add -----------------------------------------------------------------

  @Test
  void addThirtyDaysToLocalDate() {
    Result<DateMathOut> result = run("add", "2026-01-01", null, 30L, "days", "UTC");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().value()).isEqualTo("2026-01-31");
  }

  @Test
  void addOneMonthToJanuary31ClampsToFebruary28() {
    Result<DateMathOut> result = run("add", "2026-01-31", null, 1L, "months", "UTC");
    assertThat(result.value().value()).isEqualTo("2026-02-28");
  }

  @Test
  void addOneYearToLeapDayClampsToFebruary28() {
    Result<DateMathOut> result = run("add", "2024-02-29", null, 1L, "years", "UTC");
    assertThat(result.value().value()).isEqualTo("2025-02-28");
  }

  @Test
  void addOneWeekEqualsAddSevenDays() {
    Result<DateMathOut> week = run("add", "2026-06-01", null, 1L, "weeks", "UTC");
    Result<DateMathOut> days = run("add", "2026-06-01", null, 7L, "days", "UTC");
    assertThat(week.value().value()).isEqualTo(days.value().value()).isEqualTo("2026-06-08");
  }

  @Test
  void addDaysAcrossDstSpringForwardPreservesWallClock() {
    // 2026-03-08 is NY spring-forward (02:00 EST -> 03:00 EDT).
    // 06:00Z == 01:00 EST (NY). Plus 1 day wall-clock = 01:00 EDT next day = 05:00Z.
    Result<DateMathOut> result = run(
            "add", "2026-03-08T06:00:00Z", null, 1L, "days", "America/New_York");
    assertThat(result.value().value()).isEqualTo("2026-03-09T05:00:00Z");
  }

  @Test
  void addSubDayUnitToDateOnlyFails() {
    Result<DateMathOut> result = run("add", "2026-01-01", null, 1L, "hours", "UTC");
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("hours").hasMessageContaining("date-only");
  }

  // ---- diff ----------------------------------------------------------------

  @Test
  void diffDaysAndWeeksAcrossLocalDates() {
    Result<DateMathOut> days = run("diff", "2026-01-01", "2026-01-08", null, "days", "UTC");
    Result<DateMathOut> weeks = run("diff", "2026-01-01", "2026-01-08", null, "weeks", "UTC");
    assertThat(days.value().number()).isEqualTo(7L);
    assertThat(weeks.value().number()).isEqualTo(1L);
  }

  @Test
  void diffMillisAcrossDstSpringForwardDayIs23Hours() {
    // 2026-03-08 in NY is 23h long. Midnight -> midnight next day in NY = 23h of physical duration.
    Result<DateMathOut> result = run(
            "diff",
            "2026-03-08T00:00:00-05:00",
            "2026-03-09T00:00:00-04:00",
            null,
            "millis",
            "America/New_York");
    assertThat(result.value().number()).isEqualTo(23L * 60L * 60L * 1000L);
  }

  @Test
  void diffReturnsNegativeWhenStartAfterEnd() {
    Result<DateMathOut> result = run("diff", "2026-01-08", "2026-01-01", null, "days", "UTC");
    assertThat(result.value().number()).isEqualTo(-7L);
  }

  // ---- before / after ------------------------------------------------------

  @Test
  void beforeAndAfterOnLocalDates() {
    Result<DateMathOut> before = run("before", "2026-01-01", "2026-01-02", null, null, "UTC");
    Result<DateMathOut> notBefore = run("before", "2026-01-02", "2026-01-01", null, null, "UTC");
    Result<DateMathOut> after = run("after", "2026-01-02", "2026-01-01", null, null, "UTC");
    Result<DateMathOut> notAfter = run("after", "2026-01-01", "2026-01-02", null, null, "UTC");
    assertThat(before.value().flag()).isTrue();
    assertThat(notBefore.value().flag()).isFalse();
    assertThat(after.value().flag()).isTrue();
    assertThat(notAfter.value().flag()).isFalse();
  }

  @Test
  void beforeAndAfterOnInstants() {
    Result<DateMathOut> before = run(
            "before", "2026-06-15T10:00:00Z", "2026-06-15T11:00:00Z", null, null, "UTC");
    Result<DateMathOut> after = run(
            "after", "2026-06-15T11:00:00Z", "2026-06-15T10:00:00Z", null, null, "UTC");
    assertThat(before.value().flag()).isTrue();
    assertThat(after.value().flag()).isTrue();
  }

  // ---- startOf -------------------------------------------------------------

  @Test
  void startOfOnDatetimeTruncatesToEachUnit() {
    String input = "2026-06-15T10:32:45.123456Z";
    assertThat(run("startOf", input, null, null, "minute", "UTC").value().value())
            .isEqualTo("2026-06-15T10:32:00Z");
    assertThat(run("startOf", input, null, null, "hour", "UTC").value().value())
            .isEqualTo("2026-06-15T10:00:00Z");
    assertThat(run("startOf", input, null, null, "day", "UTC").value().value())
            .isEqualTo("2026-06-15T00:00:00Z");
    assertThat(run("startOf", input, null, null, "month", "UTC").value().value())
            .isEqualTo("2026-06-01T00:00:00Z");
    assertThat(run("startOf", input, null, null, "year", "UTC").value().value())
            .isEqualTo("2026-01-01T00:00:00Z");
  }

  @Test
  void startOfOnLocalDateSupportsDayMonthYear() {
    assertThat(run("startOf", "2026-06-15", null, null, "day", "UTC").value().value())
            .isEqualTo("2026-06-15");
    assertThat(run("startOf", "2026-06-15", null, null, "month", "UTC").value().value())
            .isEqualTo("2026-06-01");
    assertThat(run("startOf", "2026-06-15", null, null, "year", "UTC").value().value())
            .isEqualTo("2026-01-01");
  }

  @Test
  void startOfSubDayOnDateOnlyFails() {
    Result<DateMathOut> result = run("startOf", "2026-06-15", null, null, "hour", "UTC");
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("hour").hasMessageContaining("date-only");
  }

  // ---- failures ------------------------------------------------------------

  @Test
  void invalidUnitFails() {
    Result<DateMathOut> result = run("add", "2026-01-01", null, 1L, "fortnights", "UTC");
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause().getMessage()).contains("fortnights");
  }

  @Test
  void invalidZoneFails() {
    Result<DateMathOut> result = run("add", "2026-01-01", null, 1L, "days", "Mars/Olympus");
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause().getMessage()).contains("Mars/Olympus");
  }

  @Test
  void emptyDateFails() {
    Result<DateMathOut> result = run("add", "  ", null, 1L, "days", "UTC");
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause().getMessage()).contains("dateMath.date is required");
  }

  @Test
  void unparseableDateFails() {
    Result<DateMathOut> result = run("add", "not-a-date", null, 1L, "days", "UTC");
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause().getMessage()).contains("not-a-date");
  }

  @Test
  void unknownOpFails() {
    Result<DateMathOut> result = run("magic", "2026-01-01", null, 1L, "days", "UTC");
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause().getMessage()).contains("dateMath.op");
  }

  @Test
  void addWithNullAmountFails() {
    Result<DateMathOut> result = run("add", "2026-01-01", null, null, "days", "UTC");
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause().getMessage()).contains("dateMath.amount is required");
  }

  @Test
  void diffWithBlankEndFails() {
    Result<DateMathOut> result = run("diff", "2026-01-01", "  ", null, "days", "UTC");
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause().getMessage()).contains("dateMath.end is required");
  }

  // ---- harness -------------------------------------------------------------

  private Result<DateMathOut> run(
          String op, String date, String end, Long amount, String unit, String zone) {
    DateMathIn input = new DateMathIn(op, date, end, amount, unit, zone);
    return helper.execute(contextFactory.of(input, ExecutionMode.PREVIEW));
  }
}
