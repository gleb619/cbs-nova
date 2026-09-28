package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.starter.helper.model.CronParseIn;
import cbs.nova.starter.helper.model.CronParseOut;
import java.util.List;
import org.junit.jupiter.api.Test;

class CronParseHelperTest {

  private final CronParseHelper helper = new CronParseHelper();

  @Test
  void validateValidSixFieldExpression() {
    CronParseOut out = parse(new CronParseIn("0 0 12 * * ?", "validate", null, null));
    assertThat(out.valid()).isTrue();
    assertThat(out.timestamps()).isNull();
  }

  @Test
  void validateValidFiveFieldExpression() {
    CronParseOut out = parse(new CronParseIn("0 12 * * 1", "validate", null, null));
    assertThat(out.valid()).isTrue();
  }

  @Test
  void validateInvalidExpressionFails() {
    Result<CronParseOut> result = execute(new CronParseIn("not-a-cron", "validate", null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("cronParse");
  }

  @Test
  void validateMissingExpressionFails() {
    Result<CronParseOut> result = execute(new CronParseIn(null, "validate", null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).hasMessageContaining("expression is required");
  }

  @Test
  void nextFireSingleFixedStart() {
    CronParseOut out = parse(new CronParseIn(
            "0 0 12 * * ?",
            "nextFire",
            "2024-06-01T10:00:00Z",
            null));
    assertThat(out.valid()).isTrue();
    assertThat(out.timestamps()).containsExactly("2024-06-01T12:00:00Z");
  }

  @Test
  void nextFireCountGreaterThanOne() {
    CronParseOut out = parse(new CronParseIn(
            "0 0 12 * * ?",
            "nextFire",
            "2024-06-01T10:00:00Z",
            3));
    assertThat(out.valid()).isTrue();
    assertThat(out.timestamps()).containsExactly(
            "2024-06-01T12:00:00Z",
            "2024-06-02T12:00:00Z",
            "2024-06-03T12:00:00Z");
  }

  @Test
  void nextFireDeterminismWithFixedClock() {
    CronParseOut first = parse(new CronParseIn(
            "0 30 9 * * ?",
            "nextFire",
            "2024-03-10T01:00:00Z",
            2));
    CronParseOut second = parse(new CronParseIn(
            "0 30 9 * * ?",
            "nextFire",
            "2024-03-10T01:00:00Z",
            2));
    assertThat(first.timestamps()).isEqualTo(second.timestamps());
    assertThat(first.timestamps()).containsExactly(
            "2024-03-10T09:30:00Z",
            "2024-03-11T09:30:00Z");
  }

  @Test
  void nextFireInvalidExpressionFails() {
    Result<CronParseOut> result = execute(new CronParseIn(
            "99 99 * * *",
            "nextFire",
            "2024-06-01T10:00:00Z",
            null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nextFireBadAfterTimestampFails() {
    Result<CronParseOut> result = execute(new CronParseIn(
            "0 0 12 * * ?",
            "nextFire",
            "not-a-date",
            null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).hasMessageContaining("after");
  }

  @Test
  void modeIsCaseInsensitive() {
    CronParseOut out = parse(
            new CronParseIn("0 0 12 * * ?", "NEXTFIRE", "2024-06-01T10:00:00Z", 1));
    assertThat(out.valid()).isTrue();
    assertThat(out.timestamps()).containsExactly("2024-06-01T12:00:00Z");
  }

  @Test
  void unknownModeFails() {
    Result<CronParseOut> result = execute(new CronParseIn(
            "0 0 12 * * ?",
            "fireAllRockets",
            null,
            null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).hasMessageContaining("mode");
  }

  private CronParseOut parse(CronParseIn input) {
    Result<CronParseOut> result = execute(input);
    assertThat(result.isSuccess()).isTrue();
    return result.value();
  }

  private Result<CronParseOut> execute(CronParseIn input) {
    var ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();
    return helper.execute(ctx);
  }
}
