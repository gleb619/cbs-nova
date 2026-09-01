package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.FormatDateIn;
import cbs.nova.starter.helper.model.FormatDateOut;
import org.junit.jupiter.api.Test;

class FormatDateHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final FormatDateHelper helper = new FormatDateHelper();

  @Test
  void epochMillisInputAndIsoInstantPreset() {
    Result<FormatDateOut> result = execute(new FormatDateIn("0", "ISO_INSTANT", "UTC"));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().formatted()).isEqualTo("1970-01-01T00:00:00Z");
  }

  @Test
  void isoInstantNearMidnightRespectsZoneForDateBoundary() {
    String input = "2026-07-15T23:30:00Z";
    Result<FormatDateOut> london = execute(new FormatDateIn(input, "yyyy-MM-dd", "Europe/London"));
    Result<FormatDateOut> newYork = execute(
            new FormatDateIn(input, "yyyy-MM-dd", "America/New_York"));
    assertThat(london.isSuccess()).isTrue();
    assertThat(newYork.isSuccess()).isTrue();
    assertThat(london.value().formatted()).isEqualTo("2026-07-16");
    assertThat(newYork.value().formatted()).isEqualTo("2026-07-15");
  }

  @Test
  void rfc1123PresetProducesRfc1123String() {
    Result<FormatDateOut> result = execute(new FormatDateIn("0", "RFC_1123_DATE_TIME", "UTC"));
    assertThat(result.isSuccess()).isTrue();
    String formatted = result.value().formatted();
    assertThat(formatted).contains("GMT");
    assertThat(formatted).matches("Thu, 0?1 Jan 1970 .*");
  }

  @Test
  void londonDstChangesOffsetBetweenJulyAndJanuary() {
    String summer = executeValue(new FormatDateIn("2026-07-15T12:00:00Z", "xxx", "Europe/London"));
    String winter = executeValue(new FormatDateIn("2026-01-15T12:00:00Z", "xxx", "Europe/London"));
    assertThat(summer).isEqualTo("+01:00");
    assertThat(winter).isIn("Z", "+00:00");
  }

  @Test
  void invalidPatternFails() {
    Result<FormatDateOut> result = execute(
            new FormatDateIn("2026-03-15T12:00:00Z", "not [ a valid", "UTC"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("formatDate.pattern is invalid");
  }

  @Test
  void invalidZoneFails() {
    Result<FormatDateOut> result = execute(
            new FormatDateIn("2026-03-15T12:00:00Z", "ISO_INSTANT", "Mars/Olympus"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("formatDate.zone is not a valid zone id");
  }

  @Test
  void nullInputFails() {
    Result<FormatDateOut> result = execute(new FormatDateIn(null, "ISO_INSTANT", "UTC"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("formatDate.input is required");
  }

  private String executeValue(FormatDateIn input) {
    return execute(input).value().formatted();
  }

  private Result<FormatDateOut> execute(FormatDateIn input) {
    var ctx = contextFactory.of(input, ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }
}
