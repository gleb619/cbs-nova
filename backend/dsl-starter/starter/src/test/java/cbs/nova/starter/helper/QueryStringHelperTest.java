package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.QueryStringIn;
import cbs.nova.starter.helper.model.QueryStringOut;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QueryStringHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final QueryStringHelper helper = new QueryStringHelper();

  @Test
  void buildOrderedKeysJoinWithAmpersand() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("a", "1");
    params.put("b", "2");
    assertThat(build(params)).isEqualTo("a=1&b=2");
  }

  @Test
  void buildEncodesSpaceAsPlusViaFormEncoding() {
    Map<String, String> params = Map.of("q", "hello world");
    assertThat(build(params)).isEqualTo("q=hello+world");
  }

  @Test
  void buildEmptyMapReturnsEmptyString() {
    assertThat(build(Map.of())).isEqualTo("");
  }

  @Test
  void buildNullParamsFails() {
    Result<QueryStringOut> result = execute(new QueryStringIn("build", null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("queryString.params is required");
  }

  @Test
  void buildNullKeyFails() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("a", "1");
    params.put(null, "2");
    Result<QueryStringOut> result = execute(new QueryStringIn("build", params, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("null key");
  }

  @Test
  void buildNullValueFails() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("a", null);
    Result<QueryStringOut> result = execute(new QueryStringIn("build", params, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("null value");
  }

  @Test
  void parseSimpleKeyValuePairs() {
    assertThat(parse("a=1&b=2")).containsExactly(entry("a", "1"), entry("b", "2"));
  }

  @Test
  void parseStripsSingleLeadingQuestionMark() {
    assertThat(parse("?a=1&b=2")).containsExactly(entry("a", "1"), entry("b", "2"));
  }

  @Test
  void parsePercentDecodesValues() {
    assertThat(parse("q=hello%20world")).containsExactly(entry("q", "hello world"));
  }

  @Test
  void parseEmptyStringReturnsEmptyMap() {
    assertThat(parse("")).isEmpty();
  }

  @Test
  void parseSkipsSegmentWithoutEquals() {
    assertThat(parse("a=1&garbage&b=2")).containsExactly(entry("a", "1"), entry("b", "2"));
  }

  @Test
  void parseNullQueryStringFails() {
    Result<QueryStringOut> result = execute(new QueryStringIn("parse", null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("queryString.queryString is required");
  }

  @Test
  void roundTripParseThenBuild() {
    String rebuilt = build(parse("a=1&b=2"));
    assertThat(rebuilt).isEqualTo("a=1&b=2");
  }

  @Test
  void roundTripBuildThenParse() {
    Map<String, String> original = new LinkedHashMap<>();
    original.put("a", "1");
    original.put("b", "2");
    Map<String, String> parsed = parse(build(original));
    assertThat(parsed).containsExactly(entry("a", "1"), entry("b", "2"));
  }

  @Test
  void unknownModeFails() {
    Result<QueryStringOut> result = execute(new QueryStringIn("frobnicate", null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause())
            .hasMessage("queryString.mode must be one of build, parse, was: frobnicate");
  }

  @Test
  void nullModeFails() {
    Result<QueryStringOut> result = execute(new QueryStringIn(null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause())
            .hasMessage("queryString.mode must be one of build, parse, was: null");
  }

  private String build(Map<String, String> params) {
    return (String) execute(new QueryStringIn("build", params, null)).value().result();
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> parse(String queryString) {
    return (Map<String, String>) execute(new QueryStringIn("parse", null, queryString))
            .value()
            .result();
  }

  private static Map.Entry<String, String> entry(String key, String value) {
    return Map.entry(key, value);
  }

  private Result<QueryStringOut> execute(QueryStringIn input) {
    var ctx = contextFactory.of(input, ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }
}
