package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

class DefaultHelperRunnerPreviewTest {

  static final class MockHelper implements Executable<String, String> {
    @Override
    public @NonNull Result<String> preview(@NonNull Context<String> ctx) {
      return Result.success("PREVIEW_MOCK");
    }

    @Override
    public @NonNull Result<String> execute(@NonNull Context<String> ctx) {
      return Result.success("REAL");
    }

    @Override
    public @NonNull ExecutableDescriptor describe() {
      return new ExecutableDescriptor(
              "mock",
              "mock helper",
              String.class,
              String.class,
              false,
              "preview returns PREVIEW_MOCK",
              java.util.List.of(ParameterDescriptor.ofString("in")));
    }
  }

  @Test
  void previewModeInvokesPreviewHook() {
    var registry = new DefaultHelperRegistry();
    registry.registerHelper("mock", new MockHelper());
    var runner = new DefaultHelperRunner();
    var ctx = SimpleContext.of("input", ExecutionMode.PREVIEW);
    var result = runner.runHelper("mock", ctx, registry);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("PREVIEW_MOCK");
  }

  @Test
  void runModeInvokesExecuteHook() {
    var registry = new DefaultHelperRegistry();
    registry.registerHelper("mock", new MockHelper());
    var runner = new DefaultHelperRunner();
    var ctx = SimpleContext.of("input", ExecutionMode.RUN);
    var result = runner.runHelper("mock", ctx, registry);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("REAL");
  }

  @Test
  void describeReturnsExpectedDescriptor() {
    MockHelper helper = new MockHelper();
    ExecutableDescriptor desc = helper.describe();
    assertThat(desc.name()).isEqualTo("mock");
    assertThat(desc.inputType()).isEqualTo(String.class);
    assertThat(desc.outputType()).isEqualTo(String.class);
    assertThat(desc.hasSideEffects()).isFalse();
    assertThat(desc.previewBehavior()).isEqualTo("preview returns PREVIEW_MOCK");
    assertThat(desc.parameters()).hasSize(1);
    assertThat(desc.parameters().get(0).name()).isEqualTo("in");
  }

  @Test
  void defaultPreviewDelegatesToExecute() {
    Executable<String, String> plain = new Executable<>() {
      @Override
      public @NonNull Result<String> execute(@NonNull Context<String> ctx) {
        return Result.success("ONLY_EXEC");
      }
    };
    var ctx = SimpleContext.of("x", ExecutionMode.PREVIEW);
    assertThat(plain.preview(ctx).value()).isEqualTo("ONLY_EXEC");
  }
}
