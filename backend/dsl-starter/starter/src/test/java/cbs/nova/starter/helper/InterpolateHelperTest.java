package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.InterpolateIn;
import cbs.nova.starter.helper.model.InterpolateOut;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InterpolateHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final InterpolateHelper helper = new InterpolateHelper();

  @Test
  void singlePlaceholder() {
    Result<InterpolateOut> result = execute("Hello ${name}!", Map.of("name", "World"));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Hello World!");
  }

  @Test
  void repeatedKey() {
    Result<InterpolateOut> result = execute("${x}-${x}-${x}", Map.of("x", "abc"));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("abc-abc-abc");
  }

  @Test
  void multiplePlaceholders() {
    Result<InterpolateOut> result = execute(
            "Run ${runId} for ${customer} failed at step ${step}",
            Map.of("runId", "r-1", "customer", "acme", "step", "ship"));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result())
            .isEqualTo("Run r-1 for acme failed at step ship");
  }

  @Test
  void missingKeyErrorFailsByDefault() {
    Result<InterpolateOut> result = execute("Hi ${name}", Map.of("other", "x"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("missing key 'name'");
  }

  @Test
  void missingKeyErrorExplicit() {
    Result<InterpolateOut> result = execute("Hi ${name}", Map.of("other", "x"), "error");
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("missing key 'name'");
  }

  @Test
  void missingKeyEmptyMode() {
    Result<InterpolateOut> result = execute("Hi ${name}!", Map.of("other", "x"), "empty");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Hi !");
  }

  @Test
  void missingKeyKeepMode() {
    Result<InterpolateOut> result = execute("Hi ${name}!", Map.of("other", "x"), "keep");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Hi ${name}!");
  }

  @Test
  void onMissingIsCaseInsensitive() {
    Result<InterpolateOut> errorResult = execute("Hi ${name}", Map.of("other", "x"), "ERROR");
    Result<InterpolateOut> emptyResult = execute("Hi ${name}", Map.of("other", "x"), "Empty");
    Result<InterpolateOut> keepResult = execute("Hi ${name}", Map.of("other", "x"), "KEEP");

    assertThat(errorResult.isSuccess()).isFalse();
    assertThat(emptyResult.isSuccess()).isTrue();
    assertThat(emptyResult.value().result()).isEqualTo("Hi ");
    assertThat(keepResult.isSuccess()).isTrue();
    assertThat(keepResult.value().result()).isEqualTo("Hi ${name}");
  }

  @Test
  void dollarEscapeRendersLiteral() {
    Result<InterpolateOut> result = execute("Cost: $${amount}", Map.of("amount", "42"));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Cost: ${amount}");
    assertThat(result.value().resolvedKeys()).isEmpty();
  }

  @Test
  void dollarEscapeUnknownKeyStillRendersLiteral() {
    // Even under onMissing=error, '$${x}' must NOT trigger the missing-key failure,
    // because the leading '$$' is an escape and the '${x}' is literal output.
    Result<InterpolateOut> result = execute("$${x}", Map.of(), "error");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("${x}");
    assertThat(result.value().resolvedKeys()).isEmpty();
  }

  @Test
  void unclosedPlaceholderFails() {
    Result<InterpolateOut> result = execute("Hello ${name", Map.of("name", "World"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("unclosed '${'");
  }

  @Test
  void emptyKeyFails() {
    Result<InterpolateOut> result = execute("Hello ${}!", Map.of());
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("empty key");
  }

  @Test
  void nullTemplateFails() {
    Result<InterpolateOut> result = execute(null, Map.of("name", "World"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("template is required");
  }

  @Test
  void nullParamsTreatedAsEmpty() {
    // onMissing=keep so we see the verbatim ${x} when no params are supplied.
    Result<InterpolateOut> result = execute("Hi ${x}", null, "keep");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Hi ${x}");
    assertThat(result.value().resolvedKeys()).isEmpty();
  }

  @Test
  void presentButNullValueRendersEmpty() {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("name", null);
    Result<InterpolateOut> result = execute("Hi ${name}!", params, "error");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Hi !");
    assertThat(result.value().resolvedKeys()).containsExactly("name");
  }

  @Test
  void rendersInteger() {
    Result<InterpolateOut> result = execute("Count: ${n}", Map.of("n", 42));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Count: 42");
  }

  @Test
  void rendersBigDecimalStrippingTrailingZeros() {
    Result<InterpolateOut> result = execute("Amount: ${a}", Map.of("a", new BigDecimal("12.5000")));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Amount: 12.5");
  }

  @Test
  void rendersBoolean() {
    Result<InterpolateOut> result = execute("Active: ${flag}", Map.of("flag", true));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Active: true");
  }

  @Test
  void resolvedKeysInFirstSeenOrderAndDistinct() {
    Result<InterpolateOut> result = execute(
            "${b} ${a} ${c} ${a} ${b}",
            Map.of("a", "1", "b", "2", "c", "3"));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("2 1 3 1 2");
    assertThat(result.value().resolvedKeys()).containsExactly("b", "a", "c");
  }

  @Test
  void badOnMissingFails() {
    Result<InterpolateOut> result = execute("Hi ${name}", Map.of("name", "World"), "warn");
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause())
            .hasMessageContaining("interpolate.onMissing must be 'error', 'empty', or 'keep'");
  }

  @Test
  void keyIsTrimmed() {
    // Whitespace inside the placeholder is allowed (and stripped from the key).
    Result<InterpolateOut> result = execute("Hi ${  name  }!", Map.of("name", "World"));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Hi World!");
  }

  @Test
  void templateWithoutPlaceholdersIsUnchanged() {
    Result<InterpolateOut> result = execute("Just a plain string.", Map.of("name", "World"));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Just a plain string.");
    assertThat(result.value().resolvedKeys()).isEmpty();
  }

  private Result<InterpolateOut> execute(String template, Map<String, Object> params) {
    return execute(template, params, null);
  }

  private Result<InterpolateOut> execute(
          String template, Map<String, Object> params, String onMissing) {
    var ctx = contextFactory.of(
            new InterpolateIn(template, params, onMissing), ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }
}
