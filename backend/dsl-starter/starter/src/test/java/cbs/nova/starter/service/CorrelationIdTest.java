package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CorrelationIdTest {

  @Test
  void acceptsAllowedCharacters() {
    assertThat(CorrelationId.validated("abc-123.456:789_0")).isEqualTo("abc-123.456:789_0");
  }

  @Test
  void returnsNullWhenHeaderAbsent() {
    assertThat(CorrelationId.validated(null)).isNull();
  }

  @Test
  void trimsWhitespace() {
    assertThat(CorrelationId.validated("  corr-1  ")).isEqualTo("corr-1");
  }

  @Test
  void rejectsBlankValues() {
    assertThatThrownBy(() -> CorrelationId.validated(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid X-Correlation-Id header");
    assertThatThrownBy(() -> CorrelationId.validated("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid X-Correlation-Id header");
  }

  @Test
  void rejectsInvalidCharacters() {
    assertThatThrownBy(() -> CorrelationId.validated("a b"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid X-Correlation-Id header");
    assertThatThrownBy(() -> CorrelationId.validated("a+b"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid X-Correlation-Id header");
  }

  @Test
  void rejectsOverlengthValues() {
    assertThatThrownBy(() -> CorrelationId.validated("a".repeat(201)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid X-Correlation-Id header");
  }

  @Test
  void boundaryLengthIsValid() {
    assertThat(CorrelationId.validated("a".repeat(200))).hasSize(200);
  }

  @Test
  void fromMetadataExtractsNonBlankString() {
    assertThat(CorrelationId.fromMetadata(" corr-2 ")).isEqualTo("corr-2");
  }

  @Test
  void fromMetadataReturnsNullForBlankOrNonString() {
    assertThat(CorrelationId.fromMetadata(null)).isNull();
    assertThat(CorrelationId.fromMetadata("")).isNull();
    assertThat(CorrelationId.fromMetadata("   ")).isNull();
    assertThat(CorrelationId.fromMetadata(42)).isNull();
  }
}
