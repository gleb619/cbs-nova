package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class IdempotencyKeysTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void deriveRunIdIsDeterministicAndPrefixed() {
    String a = IdempotencyKeys.deriveRunId("ProcessA", "key-1", "anonymous");
    String b = IdempotencyKeys.deriveRunId("ProcessA", "key-1", "anonymous");

    assertThat(a).isEqualTo(b).startsWith("idem-").hasSize(5 + 32);
  }

  @Test
  void differentKeyOrNameOrCallerProducesDifferentRunId() {
    String base = IdempotencyKeys.deriveRunId("ProcessA", "key-1", "anonymous");

    assertThat(IdempotencyKeys.deriveRunId("ProcessA", "key-2", "anonymous")).isNotEqualTo(base);
    assertThat(IdempotencyKeys.deriveRunId("ProcessB", "key-1", "anonymous")).isNotEqualTo(base);
    assertThat(IdempotencyKeys.deriveRunId("ProcessA", "key-1", "api-key")).isNotEqualTo(base);
  }

  @Test
  void legacyTwoArgDerivationDefaultsToAnonymous() {
    String anonymous = IdempotencyKeys.deriveRunId("ProcessA", "key-1", "anonymous");

    assertThat(IdempotencyKeys.deriveRunId("ProcessA", "key-1")).isEqualTo(anonymous);
  }

  @Test
  void acceptsAllowedCharacters() {
    assertThat(IdempotencyKeys.isValid("abc-123.456:789_0")).isTrue();
  }

  @Test
  void rejectsInvalidKeys() {
    assertThat(IdempotencyKeys.isValid(null)).isFalse();
    assertThat(IdempotencyKeys.isValid("")).isFalse();
    assertThat(IdempotencyKeys.isValid("   ")).isFalse();
    assertThat(IdempotencyKeys.isValid("a b")).isFalse();
    assertThat(IdempotencyKeys.isValid("a/b")).isFalse();
    assertThat(IdempotencyKeys.isValid("a".repeat(201))).isFalse();
  }

  @Test
  void fingerprintIsDeterministicForSameBody() {
    String a = IdempotencyKeys.fingerprint(objectMapper, Map.of("x", 1));
    String b = IdempotencyKeys.fingerprint(objectMapper, Map.of("x", 1));

    assertThat(a).isEqualTo(b).hasSize(64);
  }

  @Test
  void fingerprintDiffersForDifferentBody() {
    String a = IdempotencyKeys.fingerprint(objectMapper, Map.of("x", 1));
    String b = IdempotencyKeys.fingerprint(objectMapper, Map.of("x", 2));

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void fingerprintTreatsNullBodyAsEmptyObject() {
    String empty = IdempotencyKeys.fingerprint(objectMapper, Map.of());
    String nulled = IdempotencyKeys.fingerprint(objectMapper, null);

    assertThat(nulled).isEqualTo(empty);
  }
}
