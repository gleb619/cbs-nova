package cbs.nova.starter.helper;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.starter.helper.model.MaskIn;
import cbs.nova.starter.helper.model.MaskOut;
import org.junit.jupiter.api.Test;

class MaskHelperTest {

  private final MaskHelper helper = new MaskHelper();

  @Test
  void defaultModeMasksCardNumberKeepingLastFour() {
    assertThat(mask("4111111111111111")).isEqualTo("*".repeat(12) + "1111");
  }

  @Test
  void defaultModeMasksIbanKeepingLastFour() {
    assertThat(mask("DE89370400440532013000")).isEqualTo("*".repeat(18) + "3000");
  }

  @Test
  void shortValueIsFullyMaskedAtFixedWidthWithoutLeakingLength() {
    assertThat(mask("abc")).isEqualTo("********");
    assertThat(mask("secret!")).isEqualTo("********");
  }

  @Test
  void emptyValueReturnsEmptyResult() {
    assertThat(mask("")).isEmpty();
  }

  @Test
  void nullValueFails() {
    Result<MaskOut> result = execute(new MaskIn(null, null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("mask.value is required");
  }

  @Test
  void edgesModeWithKeepsMasksEmailMiddle() {
    assertThat(mask(new MaskIn("a@b.com", "edges", 1, 4, null, null))).isEqualTo("a**.com");
    // last 5 code points of "a@b.com" are "b.com"
    assertThat(mask(new MaskIn("a@b.com", "edges", 1, 5, null, null))).isEqualTo("a*b.com");
  }

  @Test
  void edgesModeWithKeepFirstOnly() {
    assertThat(mask(new MaskIn("abcdef", "edges", 2, null, null, null))).isEqualTo("ab****");
  }

  @Test
  void edgesModeWithZeroKeepsMasksWholeValueAtNaturalLength() {
    assertThat(mask(new MaskIn("secret", "edges", 0, 0, null, null))).isEqualTo("******");
  }

  @Test
  void clampRuleReducesKeepsSoOneCharStaysMasked() {
    // keepFirst + keepLast >= length: keepLast is reduced, never the value returned unmasked
    assertThat(mask(new MaskIn("abcdef", "edges", 4, 4, null, null))).isEqualTo("abcd*f");
  }

  @Test
  void clampRuleWithKeepFirstBeyondLengthKeepsFirstCharMaskedTail() {
    assertThat(mask(new MaskIn("abc", "edges", 10, 0, null, null))).isEqualTo("ab*");
  }

  @Test
  void negativeKeepsAreTreatedAsZero() {
    assertThat(mask(new MaskIn("abcdef", "edges", -2, 2, null, null))).isEqualTo("****ef");
  }

  @Test
  void customMaskCharIsUsed() {
    assertThat(mask(new MaskIn("4111111111111111", null, null, null, "#", null)))
            .isEqualTo("#".repeat(12) + "1111");
  }

  @Test
  void multiCharMaskCharUsesFirstChar() {
    assertThat(mask(new MaskIn("abcdef", "fixed", null, null, "XY", 3)))
            .isEqualTo("XXX");
  }

  @Test
  void fixedModeMasksAtDefaultWidth() {
    assertThat(mask(new MaskIn("4111111111111111", "fixed", null, null, null, null)))
            .isEqualTo("********");
  }

  @Test
  void fixedModeMasksAtCustomWidth() {
    assertThat(mask(new MaskIn("4111111111111111", "fixed", null, null, "#", 4)))
            .isEqualTo("####");
  }

  @Test
  void fixedModeRejectsNonPositiveWidth() {
    Result<MaskOut> result = execute(new MaskIn("abc", "fixed", null, null, null, 0));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("mask.width must be >= 1");
  }

  @Test
  void unknownModeFails() {
    Result<MaskOut> result = execute(new MaskIn("abc", "bogus", null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("mask.mode must be 'edges' or 'fixed', was: bogus");
  }

  @Test
  void emojiStringIsNotSplitBySurrogatePairs() {
    // 5 code points: a, b, 😀, c, d
    assertThat(mask(new MaskIn("ab😀cd", "edges", 1, 1, null, null))).isEqualTo("a***d");
    // 8 code points of emoji: keeps the last 4 code points visible
    assertThat(mask("😀😀😀😀😀😀😀😀")).isEqualTo("****😀😀😀😀");
  }

  @Test
  void codePointCountingNotUtf16Length() {
    // 4 emoji = 8 UTF-16 chars but only 4 code points: below the threshold, fixed 8 masks out
    assertThat(mask("😀😀😀😀")).isEqualTo("********");
  }

  private String mask(String value) {
    return mask(new MaskIn(value, null, null, null, null, null));
  }

  private String mask(MaskIn input) {
    return execute(input).value().result();
  }

  private Result<MaskOut> execute(MaskIn input) {
    var ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();
    return helper.execute(ctx);
  }
}
