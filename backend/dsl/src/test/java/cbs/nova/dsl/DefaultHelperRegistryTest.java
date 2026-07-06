package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultHelperRegistryTest {

  private DefaultHelperRegistry registry;
  private final Executable<String, String> helper = ctx -> Result.success("ok");

  @BeforeEach
  void setUp() {
    registry = new DefaultHelperRegistry();
  }

  @Test
  void registerAndFindHelper() {
    registry.registerHelper("fmt", helper);
    assertThat(registry.findHelper("fmt")).isPresent();
  }

  @Test
  void findHelperReturnsEmptyForUnknown() {
    assertThat(registry.findHelper("missing")).isEmpty();
  }

  @Test
  void registerAndFindFunction() {
    var fn = Dsl.function("toUpper").execute(ctx -> Result.success("UP")).build();
    registry.registerFunction(fn);
    assertThat(registry.findFunction("toUpper")).isPresent();
    assertThat(registry.findFunction("toUpper").get().name()).isEqualTo("toUpper");
  }

  @Test
  void findFunctionReturnsEmptyForUnknown() {
    assertThat(registry.findFunction("missing")).isEmpty();
  }

  @Test
  void containsNameIsTrueForHelper() {
    registry.registerHelper("fmt", helper);
    assertThat(registry.containsName("fmt")).isTrue();
  }

  @Test
  void containsNameIsTrueForFunction() {
    var fn = Dsl.function("fn").execute(ctx -> Result.success("ok")).build();
    registry.registerFunction(fn);
    assertThat(registry.containsName("fn")).isTrue();
  }

  @Test
  void containsNameIsFalseForUnknown() {
    assertThat(registry.containsName("nope")).isFalse();
  }

  @Test
  void rejectsDuplicateHelperName() {
    registry.registerHelper("fmt", helper);
    assertThatThrownBy(() -> registry.registerHelper("fmt", helper))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("fmt");
  }

  @Test
  void rejectsDuplicateFunctionName() {
    var fn = Dsl.function("fn").execute(ctx -> Result.success("ok")).build();
    registry.registerFunction(fn);
    assertThatThrownBy(() -> registry.registerFunction(fn))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("fn");
  }

  @Test
  void rejectsCrossTypeDuplicateHelperThenFunction() {
    registry.registerHelper("shared", helper);
    var fn = Dsl.function("shared").execute(ctx -> Result.success("ok")).build();
    assertThatThrownBy(() -> registry.registerFunction(fn))
            .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsCrossTypeDuplicateFunctionThenHelper() {
    var fn = Dsl.function("shared").execute(ctx -> Result.success("ok")).build();
    registry.registerFunction(fn);
    assertThatThrownBy(() -> registry.registerHelper("shared", helper))
            .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void allNamesReturnsHelperAndFunctionNames() {
    registry.registerHelper("h1", helper);
    var fn = Dsl.function("f1").execute(ctx -> Result.success("ok")).build();
    registry.registerFunction(fn);
    assertThat(registry.allNames()).containsExactlyInAnyOrder("h1", "f1");
  }
}
