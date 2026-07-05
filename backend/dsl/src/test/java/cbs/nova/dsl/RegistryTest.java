package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RegistryTest {
  @Test
  void processRegistryStoresAndFinds() {
    var reg = new DefaultProcessRegistry();
    var obj = Dsl.process("P1").input(String.class).output(String.class)
            .execute(ctx -> Result.success("ok")).build();
    reg.register(obj);
    assertThat(reg.find("P1")).isPresent().contains(obj);
    assertThat(reg.find("X")).isEmpty();
  }

  @Test
  void processRegistryRejectsDuplicate() {
    var reg = new DefaultProcessRegistry();
    var obj = Dsl.process("P1").input(String.class).output(String.class)
            .execute(ctx -> Result.success("ok")).build();
    reg.register(obj);
    assertThatThrownBy(() -> reg.register(obj))
            .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void helperRegistryRejectsCrossTypeDuplicate() {
    var reg = new DefaultHelperRegistry();
    var fn = Dsl.function("myHelper").execute(ctx -> Result.success("fn")).build();
    reg.registerFunction(fn);
    assertThatThrownBy(() -> reg.registerHelper("myHelper", ctx -> Result.success("helper")))
            .isInstanceOf(IllegalArgumentException.class);
  }
}
