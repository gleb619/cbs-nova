package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.List;

class ExecutableDescriptorTest {

  @Test
  void accessorsExposeAllComponents() {
    List<ParameterDescriptor> params = List.of(ParameterDescriptor.ofString("input"));
    var descriptor = new ExecutableDescriptor(
            "echo",
            "Echoes input",
            String.class,
            String.class,
            true,
            "returns input unchanged",
            params);

    assertThat(descriptor.name()).isEqualTo("echo");
    assertThat(descriptor.description()).isEqualTo("Echoes input");
    assertThat(descriptor.inputType()).isEqualTo(String.class);
    assertThat(descriptor.outputType()).isEqualTo(String.class);
    assertThat(descriptor.hasSideEffects()).isTrue();
    assertThat(descriptor.previewBehavior()).isEqualTo("returns input unchanged");
    assertThat(descriptor.parameters()).containsExactly(ParameterDescriptor.ofString("input"));
  }

  @Test
  void nullableComponentsAcceptNull() {
    var descriptor = new ExecutableDescriptor(
            null, null, null, null, false, null, List.of());

    assertThat(descriptor.name()).isNull();
    assertThat(descriptor.description()).isNull();
    assertThat(descriptor.inputType()).isNull();
    assertThat(descriptor.outputType()).isNull();
    assertThat(descriptor.previewBehavior()).isNull();
  }

  @Test
  void parametersAcceptEmptyList() {
    var descriptor = new ExecutableDescriptor(
            "noop", null, null, null, false, null, List.of());

    assertThat(descriptor.parameters()).isEmpty();
  }

  @Test
  void hasSideEffectsFlagRoundtrips() {
    var on = new ExecutableDescriptor("a", null, null, null, true, null, List.of());
    var off = new ExecutableDescriptor("a", null, null, null, false, null, List.of());

    assertThat(on.hasSideEffects()).isTrue();
    assertThat(off.hasSideEffects()).isFalse();
  }

  @Test
  void equalsAndHashCodeBasedOnAllComponents() {
    var params = List.of(ParameterDescriptor.ofString("x"));
    var left = new ExecutableDescriptor(
            "n", "d", String.class, String.class, true, "p", params);
    var right = new ExecutableDescriptor(
            "n", "d", String.class, String.class, true, "p",
            List.of(ParameterDescriptor.ofString("x")));

    assertThat(left).isEqualTo(right).hasSameHashCodeAs(right);

    var differentName = new ExecutableDescriptor(
            "other", "d", String.class, String.class, true, "p", params);
    assertThat(left).isNotEqualTo(differentName);

    var differentSideEffects = new ExecutableDescriptor(
            "n", "d", String.class, String.class, false, "p", params);
    assertThat(left).isNotEqualTo(differentSideEffects);
  }

  @Test
  void toStringContainsComponentNames() {
    var descriptor = new ExecutableDescriptor(
            "n", "d", String.class, String.class, true, "p", List.of());

    String text = descriptor.toString();
    assertThat(text)
            .contains("name", "description", "inputType", "outputType",
                    "hasSideEffects", "previewBehavior", "parameters");
  }
}
