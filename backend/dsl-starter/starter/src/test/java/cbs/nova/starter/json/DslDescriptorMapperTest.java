package cbs.nova.starter.json;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.ParameterDescriptor;
import cbs.nova.dsl.model.ObjectDescriptor;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * T782: pins the serialized shape produced by {@link DslDescriptorMapper}.
 *
 * <p>
 * The mapper feeds {@code RunDefinitionHash}, {@code PreviewCacheKeyBuilder} and
 * {@code InputValidator} schema-cache keys. Any property add / rename / reorder silently changes
 * every definition hash and invalidates the preview cache. The 10 keys are written by
 * {@code DslDescriptorSerializer} in fixed order:
 *
 * <pre>
 * description, heartbeatTimeout, inputType, name, outputType, parameters,
 * startToCloseTimeout, taskQueue, type, version
 * </pre>
 *
 * <p>
 * Test-only. Golden JSON literals below are hand-pinned against the current Jackson 3 default
 * serialization rules ({@code Class<?>} → FQN string, {@code Duration} → ISO-8601 string, null →
 * JSON {@code null}); the test does <em>not</em> regenerate them from the mapper at runtime.
 */
class DslDescriptorMapperTest {

  private static final JsonMapper MAPPER = DslDescriptorMapper.mapper();

  // ---------- golden literal ----------

  /**
   * Fully-populated descriptor with one of every flavour of {@link ParameterDescriptor}. Hard-coded
   * literal: any drift in the serialized form (property order, key casing, class-name rendering,
   * duration format, null handling) flips the equality assertion.
   */
  static final String GOLDEN_FULL = "{\"description\":\"the description\","
          + "\"heartbeatTimeout\":\"PT5S\","
          + "\"inputType\":\"java.lang.String\","
          + "\"name\":\"myProc\","
          + "\"outputType\":\"java.lang.Integer\","
          + "\"parameters\":["
          + "{\"name\":\"a\",\"type\":\"STRING\",\"objectType\":null},"
          + "{\"name\":\"b\",\"type\":\"NUMBER\",\"objectType\":null},"
          + "{\"name\":\"c\",\"type\":\"BOOLEAN\",\"objectType\":null},"
          + "{\"name\":\"d\",\"type\":\"OBJECT\",\"objectType\":\"java.util.Map\"}"
          + "],"
          + "\"startToCloseTimeout\":\"PT30S\","
          + "\"taskQueue\":\"tq\","
          + "\"type\":\"PROCESS\","
          + "\"version\":\"v1\"}";

  /** Minimal descriptor with every optional field left null. Pins null-as-explicit-JSON-null. */
  static final String GOLDEN_MIN_NULLS = "{\"description\":null,"
          + "\"heartbeatTimeout\":null,"
          + "\"inputType\":null,"
          + "\"name\":\"x\","
          + "\"outputType\":null,"
          + "\"parameters\":null,"
          + "\"startToCloseTimeout\":null,"
          + "\"taskQueue\":null,"
          + "\"type\":\"TRANSACTION\","
          + "\"version\":null}";

  /** Empty (but explicitly set) parameters list serializes as {@code []}, not {@code null}. */
  static final String GOLDEN_EMPTY_PARAMS = "{\"description\":\"d\","
          + "\"heartbeatTimeout\":null,"
          + "\"inputType\":\"java.lang.String\","
          + "\"name\":\"p\","
          + "\"outputType\":\"java.lang.Long\","
          + "\"parameters\":[],"
          + "\"startToCloseTimeout\":null,"
          + "\"taskQueue\":null,"
          + "\"type\":\"FUNCTION\","
          + "\"version\":null}";

  // ---------- fixtures ----------

  static final class FullObj implements ObjectDescriptor {
    @Override
    public String name() {
      return "myProc";
    }
    @Override
    public DslType type() {
      return DslType.PROCESS;
    }
    @Override
    public String description() {
      return "the description";
    }
    @Override
    public Class<?> inputType() {
      return String.class;
    }
    @Override
    public Class<?> outputType() {
      return Integer.class;
    }
  }

  static final class MinNullObj implements ObjectDescriptor {
    @Override
    public String name() {
      return "x";
    }
    @Override
    public DslType type() {
      return DslType.TRANSACTION;
    }
    @Override
    public String description() {
      return null;
    }
    @Override
    public Class<?> inputType() {
      return null;
    }
    @Override
    public Class<?> outputType() {
      return null;
    }
  }

  static final class EmptyParamsObj implements ObjectDescriptor {
    @Override
    public String name() {
      return "p";
    }
    @Override
    public DslType type() {
      return DslType.FUNCTION;
    }
    @Override
    public String description() {
      return "d";
    }
    @Override
    public Class<?> inputType() {
      return String.class;
    }
    @Override
    public Class<?> outputType() {
      return Long.class;
    }
  }

  static DslDescriptor fullDescriptor() {
    return DslDescriptor.builder()
            .objectDescriptor(new FullObj())
            .parameters(List.of(
                    ParameterDescriptor.ofString("a"),
                    ParameterDescriptor.ofNumber("b"),
                    ParameterDescriptor.ofBoolean("c"),
                    ParameterDescriptor.ofObject("d", java.util.Map.class)))
            .taskQueue("tq")
            .version("v1")
            .startToCloseTimeout(Duration.ofSeconds(30))
            .heartbeatTimeout(Duration.ofSeconds(5))
            .build();
  }

  static DslDescriptor minNullDescriptor() {
    return DslDescriptor.builder()
            .objectDescriptor(new MinNullObj())
            .build();
  }

  static DslDescriptor emptyParamsDescriptor() {
    return DslDescriptor.builder()
            .objectDescriptor(new EmptyParamsObj())
            .parameters(List.of())
            .build();
  }

  // ---------- shape pins ----------

  @Nested
  class GoldenShape {

    @Test
    void fullyPopulatedDescriptorSerializesToExactGoldenString() throws Exception {
      String json = MAPPER.writeValueAsString(fullDescriptor());
      assertThat(json).isEqualTo(GOLDEN_FULL);
    }

    @Test
    void minimalDescriptorWithNullsSerializesToExactGoldenString() throws Exception {
      String json = MAPPER.writeValueAsString(minNullDescriptor());
      assertThat(json).isEqualTo(GOLDEN_MIN_NULLS);
    }

    @Test
    void emptyParametersListSerializesAsEmptyArrayNotNull() throws Exception {
      String json = MAPPER.writeValueAsString(emptyParamsDescriptor());
      assertThat(json).isEqualTo(GOLDEN_EMPTY_PARAMS);
      // Pin explicit: an empty (but set) list is `[]`, not the JSON null seen when the
      // builder field is left unset. The two states must remain distinguishable so cache
      // keys do not collide for "no parameters" vs "no parameters supplied".
      assertThat(json).contains("\"parameters\":[]");
    }

    @Test
    void propertyOrderIsFixedAlphabetical() throws Exception {
      // Pin via the golden literal: the literal above lists the keys in the order
      // description, heartbeatTimeout, inputType, name, outputType, parameters,
      // startToCloseTimeout, taskQueue, type, version. The serializer hard-codes that
      // order, but assert it directly so a future "JSON alphabetize" patch fails this
      // test rather than a downstream consumer.
      String[] expectedKeys = {
          "description", "heartbeatTimeout", "inputType", "name", "outputType",
          "parameters", "startToCloseTimeout", "taskQueue", "type", "version"
      };
      String json = MAPPER.writeValueAsString(fullDescriptor());
      int cursor = 0;
      for (String key : expectedKeys) {
        int idx = json.indexOf('"' + key + '"', cursor);
        assertThat(idx)
                .as("key %s must appear after previous keys in %s", key, json)
                .isGreaterThanOrEqualTo(cursor);
        cursor = idx + key.length();
      }
    }

    @Test
    void rawObjectDescriptorGetterDoesNotAppearInOutput() throws Exception {
      String json = MAPPER.writeValueAsString(fullDescriptor());
      // The mapper must NOT serialize `objectDescriptor` (the raw record accessor). The
      // dedicated serializer writes ten explicit keys; if any other property name leaks
      // (e.g. via Jackson's auto-discovery), hash inputs change silently.
      assertThat(json).doesNotContain("objectDescriptor");
    }
  }

  // ---------- determinism ----------

  @Nested
  class Determinism {

    @Test
    void equalDescriptorsProduceByteIdenticalSerialization() throws Exception {
      DslDescriptor a = fullDescriptor();
      DslDescriptor b = fullDescriptor();

      byte[] bytesA = MAPPER.writeValueAsBytes(a);
      byte[] bytesB = MAPPER.writeValueAsBytes(b);

      assertThat(bytesA).isEqualTo(bytesB);
      assertThat(MAPPER.writeValueAsString(a))
              .isEqualTo(MAPPER.writeValueAsString(b));
    }

    @Test
    void sameInputSerializedTwiceIsByteIdentical() throws Exception {
      DslDescriptor d = fullDescriptor();
      byte[] first = MAPPER.writeValueAsBytes(d);
      byte[] second = MAPPER.writeValueAsBytes(d);
      assertThat(first).isEqualTo(second);
      // Stable UTF-8 representation as well — this is what sha256() in RunDefinitionHash
      // consumes, so any non-determinism here would be a definition-hash break.
      assertThat(new String(first, StandardCharsets.UTF_8))
              .isEqualTo(new String(second, StandardCharsets.UTF_8));
    }
  }

  // ---------- hash sensitivity ----------

  @Nested
  class HashSensitivity {

    @Test
    void changingVersionChangesSerialization() throws Exception {
      DslDescriptor baseline = fullDescriptor();
      DslDescriptor differentVersion = DslDescriptor.builder()
              .objectDescriptor(new FullObj())
              .parameters(List.of(
                      ParameterDescriptor.ofString("a"),
                      ParameterDescriptor.ofNumber("b"),
                      ParameterDescriptor.ofBoolean("c"),
                      ParameterDescriptor.ofObject("d", java.util.Map.class)))
              .taskQueue("tq")
              .version("v2-DIFFERENT")
              .startToCloseTimeout(Duration.ofSeconds(30))
              .heartbeatTimeout(Duration.ofSeconds(5))
              .build();

      String jsonBaseline = MAPPER.writeValueAsString(baseline);
      String jsonDifferent = MAPPER.writeValueAsString(differentVersion);

      assertThat(jsonDifferent).isNotEqualTo(jsonBaseline);
      assertThat(jsonDifferent).contains("\"version\":\"v2-DIFFERENT\"");
    }

    @Test
    void changingTaskQueueChangesSerialization() throws Exception {
      DslDescriptor baseline = fullDescriptor();
      DslDescriptor differentQueue = DslDescriptor.builder()
              .objectDescriptor(new FullObj())
              .parameters(List.of(
                      ParameterDescriptor.ofString("a"),
                      ParameterDescriptor.ofNumber("b"),
                      ParameterDescriptor.ofBoolean("c"),
                      ParameterDescriptor.ofObject("d", java.util.Map.class)))
              .taskQueue("OTHER-QUEUE")
              .version("v1")
              .startToCloseTimeout(Duration.ofSeconds(30))
              .heartbeatTimeout(Duration.ofSeconds(5))
              .build();

      String jsonBaseline = MAPPER.writeValueAsString(baseline);
      String jsonDifferent = MAPPER.writeValueAsString(differentQueue);

      assertThat(jsonDifferent).isNotEqualTo(jsonBaseline);
      assertThat(jsonDifferent).contains("\"taskQueue\":\"OTHER-QUEUE\"");
    }
  }
}
