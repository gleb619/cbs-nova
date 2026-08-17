package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.registry.DefaultProcessRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultProcessRegistryTest {

  private DefaultProcessRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new DefaultProcessRegistry();
  }

  private ProcessDslObject process(String name) {
    return Dsl.process(name)
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
  }

  private ProcessDslObject process(String name, String version) {
    return Dsl.process(name)
            .version(version)
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success(version))
            .build();
  }

  @Test
  void storesAndFindsProcess() {
    registry.register(process("OrderProcess"));
    assertThat(registry.find("OrderProcess")).isPresent();
  }

  @Test
  void findReturnsEmptyForUnknown() {
    assertThat(registry.find("unknown")).isEmpty();
  }

  @Test
  void allReturnsSingleRegistered() {
    registry.register(process("P1"));
    assertThat(registry.all()).hasSize(1);
  }

  @Test
  void allReturnsAllRegistered() {
    registry.register(process("P1"));
    registry.register(process("P2"));
    assertThat(registry.all()).hasSize(2);
  }

  @Test
  void rejectsDuplicateRegistration() {
    registry.register(process("Dup"));
    assertThatThrownBy(() -> registry.register(process("Dup")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Dup")
            .hasMessageContaining("v1");
  }

  @Test
  void findReturnsCorrectProcess() {
    registry.register(process("ProcA"));
    registry.register(process("ProcB"));
    assertThat(registry.find("ProcA").get().name()).isEqualTo("ProcA");
  }

  @Test
  void allowsDifferentVersionsForSameName() {
    registry.register(process("Versioned", "v1"));
    registry.register(process("Versioned", "v2"));
    assertThat(registry.all()).hasSize(2);
    assertThat(registry.find("Versioned", "v1").get().version()).isEqualTo("v1");
    assertThat(registry.find("Versioned", "v2").get().version()).isEqualTo("v2");
    assertThat(registry.find("Versioned").get().version()).isEqualTo("v2");
  }

  @Test
  void rejectsDuplicateVersionForSameName() {
    registry.register(process("DupVersion", "v1"));
    assertThatThrownBy(() -> registry.register(process("DupVersion", "v1")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DupVersion")
            .hasMessageContaining("v1");
  }
}
