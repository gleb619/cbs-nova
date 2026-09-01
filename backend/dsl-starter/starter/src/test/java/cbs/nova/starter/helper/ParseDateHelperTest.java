package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.FormatDateIn;
import cbs.nova.starter.helper.model.FormatDateOut;
import cbs.nova.starter.helper.model.ParseDateIn;
import cbs.nova.starter.helper.model.ParseDateOut;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ParseDateHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final FormatDateHelper formatDate = new FormatDateHelper();
  private final ParseDateHelper parseDate = new ParseDateHelper();

  @Test
  void roundTripViaIsoOffsetDateTime() {
    Instant original = Instant.parse("2026-03-15T12:00:00Z");
    String formatted = format(new FormatDateIn(original.toString(), "ISO_OFFSET_DATE_TIME", "UTC"));
    String parsed = parse(new ParseDateIn(formatted, "ISO_OFFSET_DATE_TIME", "UTC"));
    assertThat(parsed).isEqualTo(original.toString());
  }

  @Test
  void localDateAtZoneStartsAtLocalMidnight() {
    Result<ParseDateOut> result = execute(
            new ParseDateIn("2026-03-15", "yyyy-MM-dd", "Europe/London"));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().iso()).isEqualTo("2026-03-15T00:00:00Z");
  }

  @Test
  void rfc1123RoundTrips() {
    Instant original = Instant.parse("1970-01-01T00:00:00Z");
    String formatted = format(
            new FormatDateIn(String.valueOf(original.toEpochMilli()), "RFC_1123_DATE_TIME", "UTC"));
    String parsed = parse(new ParseDateIn(formatted, "RFC_1123_DATE_TIME", "UTC"));
    assertThat(parsed).isEqualTo(original.toString());
  }

  @Test
  void inputNotMatchingPatternFails() {
    Result<ParseDateOut> result = execute(new ParseDateIn("not-a-date", "yyyy-MM-dd", "UTC"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("parseDate.input does not match pattern");
  }

  @Test
  void nullInputFails() {
    Result<ParseDateOut> result = execute(new ParseDateIn(null, "ISO_INSTANT", "UTC"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("parseDate.input is required");
  }

  private String format(FormatDateIn input) {
    return formatDate.execute(contextFactory.of(input, ExecutionMode.PREVIEW)).value().formatted();
  }

  private String parse(ParseDateIn input) {
    return parseDate.execute(contextFactory.of(input, ExecutionMode.PREVIEW)).value().iso();
  }

  private Result<ParseDateOut> execute(ParseDateIn input) {
    var ctx = contextFactory.of(input, ExecutionMode.PREVIEW);
    return parseDate.execute(ctx);
  }
}
