package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class PropertyResolverTest {

  @Test
  void basicSubstitution() {
    var r = new PropertyResolver(Map.of("env", "prod"), false);
    assertThat(r.resolve("loan-${env}-queue")).isEqualTo("loan-prod-queue");
  }

  @Test
  void multiplePlaceholders() {
    var r = new PropertyResolver(Map.of("a", "X", "b", "Y"), false);
    assertThat(r.resolve("${a}-${b}")).isEqualTo("X-Y");
  }

  @Test
  void missingKeyPreservesLiteralWhenNotFailing() {
    var r = new PropertyResolver(Map.of(), false);
    assertThat(r.resolve("${missing}")).isEqualTo("${missing}");
  }

  @Test
  void missingKeyThrowsWhenFailOnMissing() {
    var r = new PropertyResolver(Map.of(), true);
    Assertions.assertThatThrownBy(() -> r.resolve("${missing}"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing");
  }

  @Test
  void noPlaceholdersReturnedUnchanged() {
    var r = new PropertyResolver(Map.of("x", "y"), false);
    assertThat(r.resolve("no-placeholders-here")).isEqualTo("no-placeholders-here");
  }
}
