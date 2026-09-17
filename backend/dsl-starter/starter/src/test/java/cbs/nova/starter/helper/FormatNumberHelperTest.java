package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.FormatNumberIn;
import cbs.nova.starter.helper.model.FormatNumberOut;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FormatNumberHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final FormatNumberHelper helper = new FormatNumberHelper();

  @Test
  void integerPresetGroupsAndRoundsHalfUp() {
    assertThat(executeValue(new FormatNumberIn(1234.56, "INTEGER", null))).isEqualTo("1,235");
  }

  @Test
  void decimalPresetGroupsAndKeepsTwoFractionDigits() {
    assertThat(executeValue(new FormatNumberIn(1234567.891, "DECIMAL", null)))
            .isEqualTo("1,234,567.89");
  }

  @Test
  void percentPresetFormatsAsPercent() {
    assertThat(executeValue(new FormatNumberIn(0.1234, "PERCENT", null))).isEqualTo("12.34%");
  }

  @Test
  void currencyPresetUsesLocaleCurrencySymbol() {
    assertThat(executeValue(new FormatNumberIn(1234.5, "CURRENCY", "en-US")))
            .isEqualTo("$1,234.50");
  }

  @Test
  void customPatternUsesRoundingModeHalfUp() {
    assertThat(executeValue(new FormatNumberIn(Math.PI, "0.0000", null))).isEqualTo("3.1416");
  }

  @Test
  void deDeLocaleUsesLocaleGroupingAndDecimalSeparators() {
    assertThat(executeValue(new FormatNumberIn(1234567, "INTEGER", "de-DE")))
            .isEqualTo("1.234.567");
    assertThat(executeValue(new FormatNumberIn(1234567.891, "DECIMAL", "de-DE")))
            .isEqualTo("1.234.567,89");
  }

  @Test
  void stringInputCoercesToNumber() {
    assertThat(executeValue(new FormatNumberIn("1234.5", "DECIMAL", null)))
            .isEqualTo("1,234.50");
  }

  @Test
  void scientificNotationStringIsAccepted() {
    assertThat(executeValue(new FormatNumberIn("1.23e4", "DECIMAL", null)))
            .isEqualTo("12,300.00");
    assertThat(executeValue(new FormatNumberIn(new BigDecimal("1.23E+4"), "INTEGER", null)))
            .isEqualTo("12,300");
  }

  @Test
  void nanInputFails() {
    Result<FormatNumberOut> result = execute(new FormatNumberIn(Double.NaN, "DECIMAL", null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("not a finite number");
  }

  @Test
  void infinityInputFails() {
    Result<FormatNumberOut> result = execute(
            new FormatNumberIn(Double.POSITIVE_INFINITY, "DECIMAL", null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("not a finite number");
  }

  @Test
  void invalidPatternFails() {
    Result<FormatNumberOut> result = execute(new FormatNumberIn(1, "0.0.0", null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("formatNumber.pattern is invalid");
  }

  @Test
  void invalidLocaleFails() {
    Result<FormatNumberOut> result = execute(new FormatNumberIn(1, "DECIMAL", "not_a_locale"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause())
            .hasMessageContaining("formatNumber.locale is not a valid BCP-47 tag");
  }

  @Test
  void nonNumericStringFails() {
    Result<FormatNumberOut> result = execute(new FormatNumberIn("abc", "INTEGER", null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause())
            .hasMessageContaining("formatNumber.input is not a recognized number");
  }

  @Test
  void nullInputFails() {
    Result<FormatNumberOut> result = execute(new FormatNumberIn(null, "INTEGER", null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("formatNumber.input is required");
  }

  @Test
  void nullPatternFails() {
    Result<FormatNumberOut> result = execute(new FormatNumberIn(42, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("formatNumber.pattern is required");
  }

  private String executeValue(FormatNumberIn input) {
    return execute(input).value().formatted();
  }

  private Result<FormatNumberOut> execute(FormatNumberIn input) {
    var ctx = contextFactory.of(input, ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }
}
