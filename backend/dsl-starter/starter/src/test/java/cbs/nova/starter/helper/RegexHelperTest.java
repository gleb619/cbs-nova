package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.RegexIn;
import cbs.nova.starter.helper.model.RegexOut;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegexHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final RegexHelper helper = new RegexHelper();

  @Test
  void matchFindsDigitsInInput() {
    Result<RegexOut> result = execute(new RegexIn("match", "\\d+", "abc123", null, null, null));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().matched()).isTrue();
  }

  @Test
  void matchReportsNoMatch() {
    Result<RegexOut> result = execute(new RegexIn("match", "\\d+", "abc", null, null, null));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().matched()).isFalse();
  }

  @Test
  void extractGroupZeroReturnsFullMatch() {
    Result<RegexOut> result = execute(new RegexIn("extract", "(\\d+)", "id=42", null, 0, null));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().matched()).isTrue();
    assertThat(result.value().value()).isEqualTo("42");
  }

  @Test
  void extractSpecificGroup() {
    Result<RegexOut> group1 = execute(
            new RegexIn("extract", "(\\w+)=(\\d+)", "x=5", null, 1, null));
    assertThat(group1.value().value()).isEqualTo("x");

    Result<RegexOut> group2 = execute(
            new RegexIn("extract", "(\\w+)=(\\d+)", "x=5", null, 2, null));
    assertThat(group2.value().value()).isEqualTo("5");
  }

  @Test
  void extractNamedGroup() {
    Result<RegexOut> result = execute(
            new RegexIn("extract", "(?<num>\\d+)", "n7", null, null, "num"));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().matched()).isTrue();
    assertThat(result.value().value()).isEqualTo("7");
  }

  @Test
  void extractNoMatchReturnsMatchedFalseWithoutError() {
    Result<RegexOut> result = execute(new RegexIn("extract", "\\d+", "abc", null, 0, null));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().matched()).isFalse();
    assertThat(result.value().value()).isNull();
  }

  @Test
  void extractBadGroupIndexFails() {
    Result<RegexOut> result = execute(new RegexIn("extract", "(\\d+)", "42", null, 5, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("regex.group out of range: 5");
  }

  @Test
  void replaceAllReplacesEveryMatch() {
    Result<RegexOut> result = execute(new RegexIn("replace", "a", "banana", "X", null, null));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().value()).isEqualTo("bXnXnX");
  }

  @Test
  void replaceTreatsReplacementAsLiteral() {
    Result<RegexOut> result = execute(new RegexIn("replace", "a", "a", "$0", null, null));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().value()).isEqualTo("$0");
  }

  @Test
  void splitKeepsTrailingEmptyStrings() {
    Result<RegexOut> result = execute(new RegexIn("split", ",", "a,b,,c", null, null, null));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().values()).isEqualTo(List.of("a", "b", "", "c"));
  }

  @Test
  void invalidPatternFails() {
    Result<RegexOut> result = execute(new RegexIn("match", "(", "x", null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("regex.pattern is invalid:");
  }

  @Test
  void unknownOpFails() {
    Result<RegexOut> result = execute(new RegexIn("frobnicate", "x", "x", null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause())
            .hasMessage("regex.op must be match|extract|replace|split, was: frobnicate");
  }

  @Test
  void nullInputFails() {
    Result<RegexOut> result = execute(new RegexIn("match", "x", null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("regex.input is required");
  }

  @Test
  void nullPatternFails() {
    Result<RegexOut> result = execute(new RegexIn("match", null, "x", null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("regex.pattern is required");
  }

  @Test
  void lruCacheEvictionKeepsCapacityBound() {
    for (int i = 0; i < 65; i++) {
      Result<RegexOut> result = execute(new RegexIn("match", "a" + i, "a" + i, null, null, null));
      assertThat(result.isSuccess()).isTrue();
    }
    assertThat(RegexHelper.cachedPatternCount()).isLessThanOrEqualTo(64);
  }

  private Result<RegexOut> execute(RegexIn input) {
    var ctx = contextFactory.of(input, ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }
}
