package cbs.nova.dsl.model;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.ParameterDescriptor;
import java.util.List;
import org.junit.jupiter.api.Test;

class DescriptorsTest {

  @Test
  void buildsFunctionTypedDescriptorFromHelper() {
    var helper = new ExecutableDescriptor(
            "helperName",
            "does things",
            String.class,
            Integer.class,
            true,
            "delegates to execute",
            List.of(ParameterDescriptor.ofString("input")));

    DslDescriptor descriptor = Descriptors.from("lookupName", helper);

    assertThat(descriptor.name()).isEqualTo("lookupName");
    assertThat(descriptor.type()).isEqualTo(DslType.FUNCTION);
    assertThat(descriptor.description()).isEqualTo("does things");
    assertThat(descriptor.inputType()).isEqualTo(String.class);
    assertThat(descriptor.outputType()).isEqualTo(Integer.class);
    assertThat(descriptor.hasSideEffects()).isTrue();
    assertThat(descriptor.parameters()).containsExactly(ParameterDescriptor.ofString("input"));
  }

  @Test
  void usesExplicitNameVerbatimEvenWhenHelperNameDiffersOrIsNull() {
    var named = new ExecutableDescriptor("helperName", null, null, null, false, null, List.of());
    assertThat(Descriptors.from("explicit", named).name()).isEqualTo("explicit");

    var anonymous = new ExecutableDescriptor(null, null, null, null, false, null, List.of());
    assertThat(Descriptors.from("", anonymous).name()).isEmpty();
  }

  @Test
  void leavesProcessOnlyFieldsNull() {
    var helper = new ExecutableDescriptor("h", null, null, null, false, null, List.of());

    DslDescriptor descriptor = Descriptors.from("h", helper);

    assertThat(descriptor.taskQueue()).isNull();
    assertThat(descriptor.version()).isNull();
    assertThat(descriptor.startToCloseTimeout()).isNull();
    assertThat(descriptor.heartbeatTimeout()).isNull();
  }

  @Test
  void objectDescriptorIsImmutableAndExposesSameValues() {
    var helper = new ExecutableDescriptor(null, "d", String.class, null, false, null, List.of());

    ObjectDescriptor objectDescriptor = Descriptors.from("n", helper).objectDescriptor();

    assertThat(objectDescriptor).isInstanceOf(Record.class);
    assertThat(objectDescriptor.name()).isEqualTo("n");
    assertThat(objectDescriptor.type()).isEqualTo(DslType.FUNCTION);
    assertThat(objectDescriptor.description()).isEqualTo("d");
    assertThat(objectDescriptor.inputType()).isEqualTo(String.class);
    assertThat(objectDescriptor.outputType()).isNull();
  }
}
