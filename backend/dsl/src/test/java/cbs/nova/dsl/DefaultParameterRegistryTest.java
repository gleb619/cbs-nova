package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.registry.DefaultParameterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultParameterRegistryTest {

  private DefaultParameterRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new DefaultParameterRegistry();
  }

  @Test
  void stringAddsStringDescriptor() {
    registry.string("name");
    var descriptors = registry.descriptors();
    assertThat(descriptors).hasSize(1);
    assertThat(descriptors.get(0).name()).isEqualTo("name");
    assertThat(descriptors.get(0).type()).isEqualTo(ParameterType.STRING);
  }

  @Test
  void numberAddsNumberDescriptor() {
    registry.number("amount");
    assertThat(registry.descriptors().get(0).type()).isEqualTo(ParameterType.NUMBER);
  }

  @Test
  void boolAddsBoooleanDescriptor() {
    registry.bool("flag");
    assertThat(registry.descriptors().get(0).type()).isEqualTo(ParameterType.BOOLEAN);
  }

  @Test
  void objectAddsObjectDescriptorWithType() {
    registry.object("payload", String.class);
    var d = registry.descriptors().get(0);
    assertThat(d.type()).isEqualTo(ParameterType.OBJECT);
    assertThat(d.objectType()).isEqualTo(String.class);
  }

  @Test
  void preservesInsertionOrder() {
    registry.string("a").number("b").bool("c");
    var names = registry.descriptors().stream().map(ParameterDescriptor::name).toList();
    assertThat(names).containsExactly("a", "b", "c");
  }

  @Test
  void descriptorsReturnsUnmodifiableList() {
    registry.string("x");
    var descriptors = registry.descriptors();
    assertThatThrownBy(() -> descriptors.add(ParameterDescriptor.ofString("y")))
            .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void emptyRegistryReturnsEmptyList() {
    assertThat(registry.descriptors()).isEmpty();
  }
}
