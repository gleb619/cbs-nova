package cbs.nova.starter.security;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.properties.CbsDslRedactionProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

class JsonRedactorTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final CbsDslRedactionProperties DEFAULT_PROPERTIES = new CbsDslRedactionProperties(
          true, List.of(), "***", List.of(), 10_000_000);

  private JsonRedactor redactor() {
    return new JsonRedactor(OBJECT_MAPPER, DEFAULT_PROPERTIES);
  }

  @Test
  void masksTopLevelSensitiveKey() {
    String out = redactor().redact("{\"password\":\"secret\"}");
    assertThat(out).isEqualTo("{\"password\":\"***\"}");
  }

  @Test
  void masksNestedSensitiveKey() {
    String out = redactor().redact(
            "{\"user\":{\"apiKey\":\"abc\",\"name\":\"Alice\"}}");
    assertThat(out).contains("\"apiKey\":\"***\"");
    assertThat(out).contains("\"name\":\"Alice\"");
  }

  @Test
  void masksKeysInArrays() {
    String out = redactor().redact(
            "[{\"token\":\"t1\"},{\"token\":\"t2\"}]");
    assertThat(out).isEqualTo("[{\"token\":\"***\"},{\"token\":\"***\"}]");
  }

  @Test
  void isCaseInsensitive() {
    String out = redactor().redact(
            "{\"Password\":\"secret\",\"AUTHORIZATION\":\"Bearer x\"}");
    assertThat(out).contains("\"Password\":\"***\"");
    assertThat(out).contains("\"AUTHORIZATION\":\"***\"");
  }

  @Test
  void masksHeaderMapStyleValue() {
    String out = redactor().redact(
            "{\"headers\":{\"Authorization\":\"Bearer secret\"}}");
    assertThat(out).contains("\"Authorization\":\"***\"");
  }

  @Test
  void leavesNonMatchingKeysUnchanged() {
    String out = redactor().redact("{\"amount\":100,\"visible\":\"show me\"}");
    assertThat(out).isEqualTo("{\"amount\":100,\"visible\":\"show me\"}");
  }

  @Test
  void passesMalformedJsonThroughUnchanged() {
    String raw = "{not json";
    assertThat(redactor().redact(raw)).isEqualTo(raw);
  }

  @Test
  void passesOversizedInputThroughUnchanged() {
    CbsDslRedactionProperties small = new CbsDslRedactionProperties(
            true, List.of("secret"), "***", List.of(), 5);
    JsonRedactor r = new JsonRedactor(OBJECT_MAPPER, small);
    String raw = "{\"secret\":\"12345\"}";
    assertThat(r.redact(raw)).isEqualTo(raw);
  }

  @Test
  void disabledRedactorReturnsInputUnchanged() {
    JsonRedactor r = new JsonRedactor(OBJECT_MAPPER,
            new CbsDslRedactionProperties(false, List.of("password"), "***", List.of(),
                    10_000_000));
    String raw = "{\"password\":\"secret\"}";
    assertThat(r.redact(raw)).isEqualTo(raw);
  }

  @Test
  void nullInputReturnsNull() {
    assertThat(redactor().redact((String) null)).isNull();
    assertThat(redactor().redact((JsonNode) null)).isNull();
  }

  @Test
  void redactsJsonNodeTree() {
    JsonNode node = OBJECT_MAPPER.valueToTree(Map.of("password", "secret", "ok", true));
    JsonNode out = redactor().redact(node);
    assertThat(out.get("password").asText()).isEqualTo("***");
    assertThat(out.get("ok").asBoolean()).isTrue();
  }

  @Test
  void redactsEmbeddedJsonStringValues() {
    String raw = "{\"outputJson\":\"{\\\"password\\\":\\\"secret\\\",\\\"amount\\\":100}\"}";
    String out = redactor().redact(raw);
    assertThat(out)
            .contains("\"outputJson\":\"{\\\"password\\\":\\\"***\\\",\\\"amount\\\":100}\"");
  }

  @Test
  void customKeysAndReplacement() {
    JsonRedactor r = new JsonRedactor(OBJECT_MAPPER,
            new CbsDslRedactionProperties(true, List.of("pin"), "[REDACTED]", List.of(),
                    10_000_000));
    assertThat(r.redact("{\"pin\":\"1234\"}")).isEqualTo("{\"pin\":\"[REDACTED]\"}");
  }
}
