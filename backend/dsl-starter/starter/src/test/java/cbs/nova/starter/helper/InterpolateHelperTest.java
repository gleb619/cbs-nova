package cbs.nova.starter.helper;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.starter.helper.model.InterpolateIn;
import cbs.nova.starter.helper.model.InterpolateOut;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InterpolateHelperTest {

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

  // ---------- dotted paths ----------

  @Test
  void dottedPathPresent() {
    Map<String, Object> order = new LinkedHashMap<>();
    order.put("id", "ORD-42");
    Result<InterpolateOut> result = execute(
            "Order ${order.id}", Map.of("order", order));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Order ORD-42");
    assertThat(result.value().resolvedKeys()).containsExactly("order.id");
  }

  @Test
  void dottedPathDeepPresent() {
    Map<String, Object> address = Map.of("zip", "12345");
    Map<String, Object> order = Map.of("address", address);
    Result<InterpolateOut> result = execute(
            "Zip ${order.address.zip}", Map.of("order", order));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Zip 12345");
    assertThat(result.value().resolvedKeys()).containsExactly("order.address.zip");
  }

  @Test
  void dottedPathMissingTopKeyFollowsOnMissing() {
    Result<InterpolateOut> error = execute("${order.id}", Map.of(), "error");
    Result<InterpolateOut> empty = execute("${order.id}", Map.of(), "empty");
    Result<InterpolateOut> keep = execute("${order.id}", Map.of(), "keep");

    assertThat(error.isSuccess()).isFalse();
    assertThat(error.cause()).hasMessageContaining("missing key 'order.id'");
    assertThat(empty.value().result()).isEqualTo("");
    assertThat(keep.value().result()).isEqualTo("${order.id}");
  }

  @Test
  void dottedPathMissingNestedKeyFollowsOnMissing() {
    Map<String, Object> order = new LinkedHashMap<>();
    order.put("id", null);
    Result<InterpolateOut> error = execute("${order.id}", Map.of("order", order), "error");
    // 'id' is present (mapped to null), so it is not missing.
    assertThat(error.isSuccess()).isTrue();
    assertThat(error.value().result()).isEqualTo("");

    order.remove("id");
    Result<InterpolateOut> missing = execute("${order.id}", Map.of("order", order), "error");
    assertThat(missing.isSuccess()).isFalse();
    assertThat(missing.cause()).hasMessageContaining("missing key 'order.id'");
  }

  @Test
  void dottedPathIntermediateNotAMapFollowsOnMissing() {
    Result<InterpolateOut> result = execute(
            "${order.id}", Map.of("order", "not-a-map"), "error");
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).hasMessageContaining("missing key 'order.id'");
  }

  @Test
  void dottedPathNullAtLeafRendersEmpty() {
    Map<String, Object> order = new LinkedHashMap<>();
    order.put("id", null);
    Result<InterpolateOut> result = execute("(${order.id})", Map.of("order", order), "error");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("()");
    assertThat(result.value().resolvedKeys()).containsExactly("order.id");
  }

  @Test
  void dottedPathNullAtIntermediateRendersEmpty() {
    // A present null before the final segment is handled as a present null (renders empty),
    // not as a missing key.
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("order", null);
    Result<InterpolateOut> result = execute(
            "${order.id}", params, "error");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("");
    assertThat(result.value().resolvedKeys()).containsExactly("order.id");
  }

  // ---------- default values ----------

  @Test
  void defaultValueShortCircuitsErrorMode() {
    Result<InterpolateOut> result = execute(
            "Hi ${name:-anon}!", Map.of(), "error");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Hi anon!");
    assertThat(result.value().resolvedKeys()).isEmpty();
  }

  @Test
  void defaultValueShortCircuitsEmptyMode() {
    Result<InterpolateOut> result = execute(
            "Hi ${name:-anon}!", Map.of(), "empty");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Hi anon!");
  }

  @Test
  void defaultValueShortCircuitsKeepMode() {
    Result<InterpolateOut> result = execute(
            "Hi ${name:-anon}!", Map.of(), "keep");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Hi anon!");
  }

  @Test
  void presentNullDoesNotUseDefault() {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("name", null);
    Result<InterpolateOut> result = execute(
            "Hi ${name:-anon}!", params, "error");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Hi !");
    assertThat(result.value().resolvedKeys()).containsExactly("name");
  }

  @Test
  void dottedPathPresentNullDoesNotUseDefault() {
    Map<String, Object> order = new LinkedHashMap<>();
    order.put("id", null);
    Result<InterpolateOut> result = execute(
            "(${order.id:-unknown})", Map.of("order", order), "error");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("()");
    assertThat(result.value().resolvedKeys()).containsExactly("order.id");
  }

  @Test
  void defaultValueIsLiteralAndNotReInterpolated() {
    Result<InterpolateOut> result = execute(
            "${missing:-${foo}}", Map.of("foo", "bar"), "error");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("${foo}");
    assertThat(result.value().resolvedKeys()).isEmpty();
  }

  @Test
  void defaultValueEmptyStringIsUsed() {
    Result<InterpolateOut> result = execute(
            "[${missing:-}]", Map.of(), "error");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("[]");
  }

  @Test
  void emptyKeyWithDefaultStillFails() {
    Result<InterpolateOut> result = execute("${:-default}", Map.of(), "error");
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).hasMessageContaining("empty key");
  }

  // ---------- combined dotted + default ----------

  @Test
  void dottedPathWithDefaultPresent() {
    Map<String, Object> order = Map.of("id", "ORD-1");
    Result<InterpolateOut> result = execute(
            "${order.id:-unknown}", Map.of("order", order));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("ORD-1");
    assertThat(result.value().resolvedKeys()).containsExactly("order.id");
  }

  @Test
  void dottedPathWithDefaultMissingUsesDefault() {
    Result<InterpolateOut> result = execute(
            "Order: ${order.id:-unknown}", Map.of(), "error");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Order: unknown");
    assertThat(result.value().resolvedKeys()).isEmpty();
  }

  @Test
  void dottedPathWithDefaultMissingNestedUsesDefault() {
    Map<String, Object> order = new LinkedHashMap<>();
    Result<InterpolateOut> result = execute(
            "Order: ${order.id:-unknown}", Map.of("order", order), "error");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Order: unknown");
  }

  // ---------- escaping edge cases ----------

  @Test
  void dollarEscapeWithDefaultRendersLiteral() {
    // The existing $$ escape must still prevent the following placeholder from being parsed,
    // even when the placeholder body contains the new :- default delimiter.
    Result<InterpolateOut> result = execute(
            "$${name:-anon}", Map.of(), "error");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("${name:-anon}");
    assertThat(result.value().resolvedKeys()).isEmpty();
  }

  @Test
  void defaultValueUsesFirstDelimiterOnly() {
    // The first :- splits key and default; any later :- stays inside the literal default.
    Result<InterpolateOut> result = execute(
            "${x:-a:-b}", Map.of(), "error");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("a:-b");
  }

  private Result<InterpolateOut> execute(String template, Map<String, Object> params) {
    return execute(template, params, null);
  }

  private Result<InterpolateOut> execute(
          String template, Map<String, Object> params, String onMissing) {
    var ctx = SimpleContext.<InterpolateIn>builder()
            .body(new InterpolateIn(template, params, onMissing)).mode(ExecutionMode.PREVIEW)
            .build();
    return helper.execute(ctx);
  }
}
