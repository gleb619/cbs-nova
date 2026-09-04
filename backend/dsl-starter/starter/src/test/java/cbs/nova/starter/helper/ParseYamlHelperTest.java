package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.ParseYamlIn;
import cbs.nova.starter.helper.model.ParseYamlOut;
import java.util.List;
import java.util.Map;
import javax.script.ScriptEngineManager;
import org.junit.jupiter.api.Test;

class ParseYamlHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final ParseYamlHelper helper = new ParseYamlHelper();

  @Test
  void simpleKeyValue() {
    Result<ParseYamlOut> result = execute("name: cbs-nova\nversion: \"1.0\"\n");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().data())
            .containsEntry("name", "cbs-nova")
            .containsEntry("version", "1.0");
  }

  @Test
  void nestedMapsAndLists() {
    String yaml = """
            spec:
              replicas: 3
              image: nginx:1.27
              ports:
                - name: http
                  port: 80
                - name: https
                  port: 443
            """;
    Result<ParseYamlOut> result = execute(yaml);
    assertThat(result.isSuccess()).isTrue();
    Map<String, Object> data = result.value().data();
    @SuppressWarnings("unchecked")
    Map<String, Object> spec = (Map<String, Object>) data.get("spec");
    assertThat(spec).containsEntry("replicas", 3).containsEntry("image", "nginx:1.27");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> ports = (List<Map<String, Object>>) spec.get("ports");
    assertThat(ports).hasSize(2);
    assertThat(ports.get(0)).containsEntry("name", "http").containsEntry("port", 80);
    assertThat(ports.get(1)).containsEntry("name", "https").containsEntry("port", 443);
  }

  @Test
  void anchorsAndAliases() {
    String yaml = """
            defaults: &defaults
              replicas: 2
              image: busybox
            prod:
              <<: *defaults
              image: nginx
            """;
    Result<ParseYamlOut> result = execute(yaml);
    assertThat(result.isSuccess()).isTrue();
    Map<String, Object> data = result.value().data();
    @SuppressWarnings("unchecked")
    Map<String, Object> prod = (Map<String, Object>) data.get("prod");
    assertThat(prod).containsEntry("replicas", 2).containsEntry("image", "nginx");
  }

  @Test
  void multilineLiteralAndFoldedScalars() {
    String yaml = "literal: |\n  line one\n  line two\nfolded: >\n  one\n  two\n";
    Result<ParseYamlOut> result = execute(yaml);
    assertThat(result.isSuccess()).isTrue();
    Map<String, Object> data = result.value().data();
    assertThat(data.get("literal")).isEqualTo("line one\nline two\n");
    assertThat(data.get("folded")).isEqualTo("one two\n");
  }

  @Test
  void integersFloatsAndScientificNotation() {
    String yaml = "i: 42\nf: 3.14\ne: 1.5e10\n";
    Result<ParseYamlOut> result = execute(yaml);
    assertThat(result.isSuccess()).isTrue();
    Map<String, Object> data = result.value().data();
    assertThat(data.get("i")).isEqualTo(42);
    assertThat(data.get("f")).isEqualTo(3.14);
    assertThat(data.get("e")).isEqualTo(1.5e10);
  }

  @Test
  void yaml12BooleansOnly() {
    // Quoted keys so the map's String key set is preserved; YAML 1.2 implicit typing only
    // matters for the values, where yes/no/on/off must stay plain strings.
    String yaml = "\"yes\": yes\n\"no\": no\n\"on\": on\n\"off\": off\n\"true\": true\n\"false\": false\n";
    Result<ParseYamlOut> result = execute(yaml);
    assertThat(result.isSuccess()).isTrue();
    Map<String, Object> data = result.value().data();
    // YAML 1.2: only true/false are booleans; yes/no/on/off stay as strings.
    assertThat(data.get("yes")).isEqualTo("yes");
    assertThat(data.get("no")).isEqualTo("no");
    assertThat(data.get("on")).isEqualTo("on");
    assertThat(data.get("off")).isEqualTo("off");
    assertThat(data.get("true")).isEqualTo(true);
    assertThat(data.get("false")).isEqualTo(false);
  }

  @Test
  void nullTildeAndEmptyValue() {
    String yaml = "a: ~\nb:\nc: ''\n";
    Result<ParseYamlOut> result = execute(yaml);
    assertThat(result.isSuccess()).isTrue();
    Map<String, Object> data = result.value().data();
    assertThat(data.get("a")).isNull();
    assertThat(data.get("b")).isNull();
    assertThat(data.get("c")).isEqualTo("");
  }

  @Test
  void nullPayloadFails() {
    Result<ParseYamlOut> result = execute(null);
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("parseYaml.payload is required");
  }

  @Test
  void blankPayloadFails() {
    Result<ParseYamlOut> result = execute("   \n  \t  ");
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("parseYaml.payload is required");
  }

  @Test
  void emptyDocumentFails() {
    // "---" parses to null under SafeConstructor; top-level must be a mapping.
    Result<ParseYamlOut> result = execute("---\n");
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("parseYaml");
  }

  @Test
  void malformedYamlFails() {
    // Tab character inside a block scalar — snakeyaml rejects.
    Result<ParseYamlOut> result = execute("a:\n\tb: 1\n");
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("parseYaml: malformed YAML");
  }

  @Test
  void duplicateKeyFails() {
    // setAllowDuplicateKeys(false) — strict YAML 1.2 parsers reject duplicates.
    Result<ParseYamlOut> result = execute("a: 1\na: 2\n");
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("parseYaml: malformed YAML");
  }

  @Test
  void scriptEngineManagerPayloadDoesNotInstantiate() {
    // Regression for snakeyaml CVE-2017-18640.
    String payload = "!!javax.scripting.ScriptEngineManager {}";
    Result<ParseYamlOut> result = execute(payload);
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    // Belt-and-suspenders: prove the engine was never constructed by this test classloader.
    ScriptEngineManager probe = new ScriptEngineManager();
    assertThat(probe.getEngineByName("nashorn")).isNull();
    assertThat(result.cause()).hasMessageContaining("parseYaml: malformed YAML");
  }

  private Result<ParseYamlOut> execute(String payload) {
    var ctx = contextFactory.of(new ParseYamlIn(payload), ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }
}
