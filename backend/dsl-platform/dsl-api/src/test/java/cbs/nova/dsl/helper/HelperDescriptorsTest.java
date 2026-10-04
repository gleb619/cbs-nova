package cbs.nova.dsl.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

class HelperDescriptorsTest {

  @Test
  void fillsInputAndOutputFromGenericBinding() {
    var helper = new SimpleBoundHelper();

    ExecutableDescriptor descriptor = HelperDescriptors.describe(helper);

    assertThat(descriptor.inputType()).isEqualTo(String.class);
    assertThat(descriptor.outputType()).isEqualTo(Integer.class);
  }

  @Test
  void usesAnnotationDescriptionWhenNoDescribeOverride() {
    var helper = new DescribedHelper();

    ExecutableDescriptor descriptor = HelperDescriptors.describe(helper);

    assertThat(descriptor.description()).isEqualTo("annotation description");
  }

  @Test
  void keepsExplicitDescribeValues() {
    var helper = new ExplicitHelper();

    ExecutableDescriptor descriptor = HelperDescriptors.describe(helper);

    assertThat(descriptor.description()).isEqualTo("explicit");
    assertThat(descriptor.inputType()).isEqualTo(Long.class);
    assertThat(descriptor.outputType()).isEqualTo(Boolean.class);
  }

  @Helper(name = "simpleBound")
  static class SimpleBoundHelper implements Executable<String, Integer> {
    @Override
    public @NonNull Result<Integer> execute(@NonNull Context<String> ctx) {
      return Result.success(1);
    }
  }

  @Helper(name = "described", description = "annotation description")
  static class DescribedHelper implements Executable<String, Integer> {
    @Override
    public @NonNull Result<Integer> execute(@NonNull Context<String> ctx) {
      return Result.success(1);
    }
  }

  @Helper(name = "explicit")
  static class ExplicitHelper implements Executable<String, Integer> {
    @Override
    public @NonNull Result<Integer> execute(@NonNull Context<String> ctx) {
      return Result.success(1);
    }

    @Override
    public @NonNull ExecutableDescriptor describe() {
      return new ExecutableDescriptor(
              "explicit", "explicit", Long.class, Boolean.class, java.util.List.of());
    }
  }
}
