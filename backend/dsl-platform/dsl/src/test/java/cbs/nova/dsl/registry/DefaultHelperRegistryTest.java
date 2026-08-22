package cbs.nova.dsl.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultHelperRegistryTest {

  private final Executable<String, String> helper = ctx -> Result.success("ok");
  private DefaultHelperRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new DefaultHelperRegistry();
  }

  @Test
  void registerHelperWithExecutableAndFind() {
    registry.registerHelper("fmt", helper);

    assertThat(registry.findHelper("fmt")).contains(helper);
  }

  @Test
  void registerHelperViaSupplierIsLazy() {
    AtomicInteger invocations = new AtomicInteger();
    Supplier<Executable<?, ?>> supplier = () -> {
      invocations.incrementAndGet();
      return helper;
    };

    registry.registerHelper("lazy", supplier);

    // Registration must not invoke the supplier.
    assertThat(invocations.get()).isZero();
    assertThat(registry.containsName("lazy")).isTrue();

    // Lookup resolves the supplier on demand.
    assertThat(registry.findHelper("lazy")).contains(helper);
    assertThat(invocations.get()).isEqualTo(1);
  }

  @Test
  void lazySupplierIsResolvedOnlyOnLookup() {
    AtomicInteger invocations = new AtomicInteger();
    Supplier<Executable<?, ?>> supplier = () -> {
      invocations.incrementAndGet();
      return helper;
    };

    registry.registerHelper("added", supplier);
    registry.registerFunction(Dsl.function("func").execute(ctx -> Result.success("ok")).build());

    // Registering other entries and querying names must not force resolution.
    assertThat(registry.allNames()).containsExactlyInAnyOrder("added", "func");
    assertThat(invocations.get()).isZero();

    registry.findHelper("added");
    registry.findHelper("added");
    assertThat(invocations.get()).isEqualTo(2);
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
    registry.registerFunction(Dsl.function("fn").execute(ctx -> Result.success("ok")).build());

    assertThat(registry.containsName("fn")).isTrue();
  }

  @Test
  void containsNameIsFalseForUnknown() {
    assertThat(registry.containsName("nope")).isFalse();
  }

  @Test
  void rejectsDuplicateExecutableHelperName() {
    registry.registerHelper("fmt", helper);

    assertThatThrownBy(() -> registry.registerHelper("fmt", helper))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("fmt");
  }

  @Test
  void rejectsDuplicateSupplierHelperNameWithoutResolving() {
    registry.registerHelper("dup", helper);
    AtomicInteger invocations = new AtomicInteger();
    Supplier<Executable<?, ?>> supplier = () -> {
      invocations.incrementAndGet();
      return helper;
    };

    assertThatThrownBy(() -> registry.registerHelper("dup", supplier))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dup");

    // The rejected supplier must never have been invoked.
    assertThat(invocations.get()).isZero();
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
    registry.registerFunction(Dsl.function("f1").execute(ctx -> Result.success("ok")).build());

    assertThat(registry.allNames()).containsExactlyInAnyOrder("h1", "f1");
  }
}