package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.error.YAMLException;

class YamlSupportTest {

  // ---------- safeLoader: scalar coercion (YAML 1.2 semantics) ----------

  @Test
  void safeLoaderParsesTrueFalseAsBooleans() {
    Object loaded = YamlSupport.safeLoader().load("a: true\nb: false\n");
    assertThat(loaded).isInstanceOf(Map.class);
    Map<?, ?> map = (Map<?, ?>) loaded;
    assertThat(map.get("a")).isEqualTo(Boolean.TRUE);
    assertThat(map.get("b")).isEqualTo(Boolean.FALSE);
  }

  @Test
  void safeLoaderParsesMixedCaseBooleansAsBooleans() {
    // The Yaml12Resolver accepts the canonical spellings true/True/TRUE/false/False/FALSE;
    // mixed-case variants (e.g. tRue) fall through to the generic string resolver.
    Object loaded = YamlSupport.safeLoader()
            .load("- true\n- True\n- TRUE\n- false\n- False\n- FALSE\n");
    assertThat((List<Object>) loaded)
            .containsExactly(Boolean.TRUE, Boolean.TRUE, Boolean.TRUE,
                    Boolean.FALSE, Boolean.FALSE, Boolean.FALSE);
  }

  @Test
  void safeLoaderLeavesLowercaseYaml11AliasesAsStrings() {
    // The Yaml12Resolver clears implicit resolvers on lowercase 'y', 'n', 'o', so
    // YAML 1.1 aliases (yes/no/on/off) stay as plain strings.
    Object loaded = YamlSupport.safeLoader().load("- yes\n- no\n- on\n- off\n");
    assertThat((List<Object>) loaded).containsExactly("yes", "no", "on", "off");
  }

  @Test
  void safeLoaderLeavesMixedCaseBoolLikeStringsVerbatim() {
    // Mixed-case variants that are not on the canonical 1.2 list fall through to the
    // generic string resolver — none of the registered BOOL patterns matches them.
    Object loaded = YamlSupport.safeLoader().load("- tRue\n- fAlse\n- yEs\n- nO\n");
    assertThat((List<Object>) loaded).containsExactly("tRue", "fAlse", "yEs", "nO");
  }

  @Test
  void safeLoaderTreatsYaml11BoolAliasesAsStrings() {
    // YAML 1.1's yes/no/on/off stay plain strings under the YAML 1.2 resolver
    // — DSL authors must not accidentally rely on the legacy trap.
    Object loaded = YamlSupport.safeLoader().load("- yes\n- no\n- on\n- off\n");
    assertThat((List<Object>) loaded).containsExactly("yes", "no", "on", "off");
  }

  @Test
  void safeLoaderParsesIntegersAndFloats() {
    Object loaded = YamlSupport.safeLoader().load("i: 42\nf: 3.14\n");
    Map<?, ?> map = (Map<?, ?>) loaded;
    assertThat(map.get("i")).isEqualTo(42);
    assertThat(map.get("f")).isEqualTo(3.14d);
  }

  @Test
  void safeLoaderTreatsUnquotedOnePointZeroAsStringNotFloat() {
    // snakeyaml quotes "1.0" on dump to disambiguate from a float; loading must round-trip.
    Object loaded = YamlSupport.safeLoader().load("version: '1.0'\n");
    assertThat(((Map<?, ?>) loaded).get("version")).isEqualTo("1.0");
  }

  @Test
  void safeLoaderParsesNull() {
    Object loaded = YamlSupport.safeLoader().load("a: null\nb: ~\n");
    Map<?, ?> map = (Map<?, ?>) loaded;
    assertThat(map.get("a")).isNull();
    assertThat(map.get("b")).isNull();
  }

  @Test
  void safeLoaderParsesEmptyInputAsNull() {
    assertThat((Object) YamlSupport.safeLoader().load("")).isNull();
  }

  @Test
  void safeLoaderParsesNestedStructures() {
    Map<String, Object> ports = new LinkedHashMap<>();
    ports.put("name", "http");
    ports.put("port", 80);
    Map<String, Object> spec = new LinkedHashMap<>();
    spec.put("replicas", 3);
    spec.put("ports", List.of(ports));
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("spec", spec);
    String yaml = "spec:\n  replicas: 3\n  ports:\n    - name: http\n      port: 80\n";

    Object loaded = YamlSupport.safeLoader().load(yaml);
    assertThat(loaded).isEqualTo(root);
  }

  // ---------- safeLoader: safety boundaries ----------

  @Test
  void safeLoaderRejectsDuplicateKeys() {
    String dup = "a: 1\na: 2\n";
    assertThatThrownBy(() -> YamlSupport.safeLoader().load(dup))
            .isInstanceOf(YAMLException.class);
  }

  @Test
  void safeLoaderRejectsCustomGlobalTag() {
    String dangerous = "!!python/object:os.system ['echo unsafe']\n";
    assertThatThrownBy(() -> YamlSupport.safeLoader().load(dangerous))
            .isInstanceOf(YAMLException.class);
  }

  @Test
  void safeLoaderRejectsAliasExplosion() {
    StringBuilder sb = new StringBuilder();
    // Anchor + 51 aliases — one more than the configured limit of 50.
    sb.append("a: &anchor [1, 2, 3]\n");
    sb.append("references:\n");
    for (int i = 0; i < 51; i++) {
      sb.append("  - *anchor\n");
    }
    String overLimit = sb.toString();
    assertThatThrownBy(() -> YamlSupport.safeLoader().load(overLimit))
            .isInstanceOf(YAMLException.class);
  }

  @Test
  void safeLoaderAllowsAliasesUpToLimit() {
    StringBuilder sb = new StringBuilder();
    sb.append("a: &anchor [1, 2, 3]\n");
    sb.append("references:\n");
    for (int i = 0; i < 50; i++) {
      sb.append("  - *anchor\n");
    }
    Object loaded = YamlSupport.safeLoader().load(sb.toString());
    assertThat(loaded).isInstanceOf(Map.class);
  }

  @Test
  void safeLoaderEnforcesCodePointLimit() {
    // 3 MiB + 1 char of slack exceeds YAML_MAX_CODE_POINTS.
    StringBuilder sb = new StringBuilder("key: ");
    int target = 3 * 1024 * 1024 + 1;
    while (sb.length() < target) {
      sb.append('a');
    }
    sb.append('\n');
    String overSized = sb.toString();
    assertThatThrownBy(() -> YamlSupport.safeLoader().load(overSized))
            .isInstanceOf(YAMLException.class);
  }

  // ---------- safeLoader: constructor wiring ----------

  @Test
  void safeLoaderRejectsAnyGlobalTagUnderAllAliases() {
    // Every spelling of an unknown global tag must be rejected — this pins the TagInspector
    // blanket-deny regardless of which exotic alias the loader is asked about.
    String[] samples = {
        "v: !!python/object:os.system ['id']\n",
        "v: !custom\n  any: 1\n",
        "v: !!java.io.File '/etc/passwd'\n"
    };
    for (String sample : samples) {
      assertThatThrownBy(() -> YamlSupport.safeLoader().load(sample))
              .as("sample: %s", sample.replace("\n", "\\n"))
              .isInstanceOf(YAMLException.class);
    }
  }

  // ---------- dumper: output style ----------

  @Test
  void dumperProducesBlockStyleNotFlow() {
    String dumped = YamlSupport.dumper().dump(Map.of("a", 1, "b", 2));
    assertThat(dumped).doesNotContain("{").doesNotContain("}");
    assertThat(dumped).contains("a:").contains("b:");
  }

  @Test
  void dumperRoundTripsThroughSafeLoader() {
    Map<String, Object> root = new LinkedHashMap<>();
    Map<String, Object> child = new LinkedHashMap<>();
    child.put("port", 80);
    child.put("tags", List.of("a", "b"));
    root.put("spec", child);

    String dumped = YamlSupport.dumper().dump(root);
    Object loaded = YamlSupport.safeLoader().load(dumped);
    assertThat(loaded).isEqualTo(root);
  }

  @Test
  void dumperIsDeterministic() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("a", 1);
    data.put("b", List.of(1, 2, 3));
    String first = YamlSupport.dumper().dump(data);
    String second = YamlSupport.dumper().dump(data);
    assertThat(first).isEqualTo(second);
  }

  // ---------- cross-check: resolver does not leave BOOL on the inherited YAML 1.1 triggers
  // ----------

  @Test
  void safeLoaderDoesNotMistakeEmptyStringForBool() {
    Object loaded = YamlSupport.safeLoader().load("v: ''\n");
    assertThat(((Map<?, ?>) loaded).get("v")).isEqualTo("");
  }

  @Test
  void safeLoaderPreservesTagExplicitString() {
    // Explicit !!str override must still win — SafeConstructor honors explicit tags.
    Object loaded = YamlSupport.safeLoader().load("v: !!str 42\n");
    assertThat(((Map<?, ?>) loaded).get("v")).isEqualTo("42");
  }

  @Test
  void safeLoaderResolvesImplicitStringForUnquotedAlpha() {
    Object loaded = YamlSupport.safeLoader().load("name: cbs-nova\n");
    assertThat(((Map<?, ?>) loaded).get("name")).isEqualTo("cbs-nova");
  }
}
