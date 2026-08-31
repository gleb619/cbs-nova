package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdempotencyKeysTest {

  @Test
  void deriveRunIdIsDeterministicAndPrefixed() {
    String a = IdempotencyKeys.deriveRunId("ProcessA", "key-1");
    String b = IdempotencyKeys.deriveRunId("ProcessA", "key-1");

    assertThat(a).isEqualTo(b).startsWith("idem-").hasSize(5 + 32);
  }

  @Test
  void differentKeyOrNameProducesDifferentRunId() {
    String base = IdempotencyKeys.deriveRunId("ProcessA", "key-1");

    assertThat(IdempotencyKeys.deriveRunId("ProcessA", "key-2")).isNotEqualTo(base);
    assertThat(IdempotencyKeys.deriveRunId("ProcessB", "key-1")).isNotEqualTo(base);
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
}
