package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.FormatYamlIn;
import cbs.nova.starter.helper.model.FormatYamlOut;
import cbs.nova.starter.helper.model.ParseYamlIn;
import cbs.nova.starter.helper.model.ParseYamlOut;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FormatYamlHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final FormatYamlHelper helper = new FormatYamlHelper();
  private final ParseYamlHelper parseHelper = new ParseYamlHelper();

  @Test
  void formatsSimpleMap() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "cbs-nova");
    data.put("version", "1.0");
    Result<FormatYamlOut> result = execute(data);
    assertThat(result.isSuccess()).isTrue();
    // snakeyaml quotes the string "1.0" to disambiguate it from a float literal.
    assertThat(result.value().yaml()).isEqualTo("name: cbs-nova\nversion: '1.0'\n");
  }

  @Test
  void roundTripNestedMapsAndLists() {
    Map<String, Object> ports = new LinkedHashMap<>();
    ports.put("name", "http");
    ports.put("port", 80);
    Map<String, Object> spec = new LinkedHashMap<>();
    spec.put("replicas", 3);
    spec.put("ports", List.of(ports));
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("spec", spec);

    Result<FormatYamlOut> formatted = execute(root);
    assertThat(formatted.isSuccess()).isTrue();

    var ctx = contextFactory.of(new ParseYamlIn(formatted.value().yaml()), ExecutionMode.PREVIEW);
    Result<ParseYamlOut> parsed = parseHelper.execute(ctx);
    assertThat(parsed.isSuccess()).isTrue();
    assertThat(parsed.value().data()).isEqualTo(root);
  }

  @Test
  void formatsList() {
    Result<FormatYamlOut> result = execute(List.of("a", "b", "c"));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().yaml()).isEqualTo("- a\n- b\n- c\n");
  }

  @Test
  void formatsScalars() {
    assertThat(execute("plain").value().yaml()).isEqualTo("plain\n");
    assertThat(execute(42).value().yaml()).isEqualTo("42\n");
    assertThat(execute(true).value().yaml()).isEqualTo("true\n");
    assertThat(execute(3.14).value().yaml()).isEqualTo("3.14\n");
  }

  @Test
  void determinism() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("a", 1);
    data.put("b", List.of(1, 2, 3));
    String first = execute(data).value().yaml();
    String second = execute(data).value().yaml();
    assertThat(first).isEqualTo(second);
  }

  @Test
  void nullDataFails() {
    Result<FormatYamlOut> result = execute(null);
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("formatYaml.data is required");
  }

  @Test
  void emptyMapFails() {
    Result<FormatYamlOut> result = execute(Map.of());
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("formatYaml.data is required");
  }

  @Test
  void emptyListFails() {
    Result<FormatYamlOut> result = execute(List.of());
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("formatYaml.data is required");
  }

  private Result<FormatYamlOut> execute(Object data) {
    var ctx = contextFactory.of(new FormatYamlIn(data), ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }
}
