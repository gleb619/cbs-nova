package cbs.nova.dsl.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReplaceableDistinctnessTest {

  @Test
  void replaceablesFromDifferentMethodsAreIndependent() {
    var support = new SingletonSupport() {
      private final Scope scope = SingletonScope.of();

      @Override
      public Scope getScope() {
        return scope;
      }

      public Replaceable<String> a() {
        return replaceable();
      }

      public Replaceable<String> b() {
        return replaceable();
      }
    };

    var a = support.a();
    var b = support.b();

    a.replace("A");
    b.replace("B");

    assertThat(a.get()).isEqualTo("A");
    assertThat(b.get()).isEqualTo("B");
  }
}
