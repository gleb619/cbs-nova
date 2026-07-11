package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.runner.DefaultProcessRunner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class ProcessPreviewDescribeTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final ExecutionTraceCollector traceCollector = new ExecutionTraceCollector();

  @Test
  void processWithPreviewReturnsMockInPreviewMode() {
    var executeCalled = new AtomicBoolean(false);
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> {
              executeCalled.set(true);
              return Result.success("EXEC");
            })
            .preview(ctx -> Result.success("PREVIEW_MOCK"))
            .build();

    var runner = new DefaultProcessRunner(traceCollector, contextFactory);
    var ctx = contextFactory.of("input", ExecutionMode.PREVIEW);
    var result = runner.run(process, ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("PREVIEW_MOCK");
    assertThat(executeCalled.get()).isFalse();
  }

  @Test
  void processWithoutPreviewDelegatesToExecuteInPreviewMode() {
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("EXEC"))
            .build();

    var runner = new DefaultProcessRunner(traceCollector, contextFactory);
    var ctx = contextFactory.of("input", ExecutionMode.PREVIEW);
    var result = runner.run(process, ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("EXEC");
  }

  @Test
  void describeReturnsCorrectFields() {
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();

    DslDescriptor desc = process.describe();

    assertThat(desc.name()).isEqualTo("P");
    assertThat(desc.type()).isEqualTo(DslObject.DslType.PROCESS);
    assertThat(desc.inputType()).isEqualTo(String.class);
    assertThat(desc.outputType()).isEqualTo(String.class);
    assertThat(desc.hasCompensation()).isFalse();
    assertThat(desc.hasSideEffects()).isTrue();
    assertThat(desc.previewBehavior()).isEqualTo("delegates to execute");
    assertThat(desc.taskQueue()).isEqualTo("P-queue");
    assertThat(desc.version()).isEqualTo("v1");
    assertThat(desc.parameters()).isEmpty();
  }

  @Test
  void describeReportsCompensationWhenPresent() {
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .compensation(ctx -> Result.success(null))
            .build();

    DslDescriptor desc = process.describe();
    assertThat(desc.hasCompensation()).isTrue();
  }

  @Test
  void customDescriptorSupplierOverridesDefault() {
    var custom = new DslDescriptor(
            "P",
            DslObject.DslType.PROCESS,
            "custom description",
            String.class,
            String.class,
            false,
            false,
            "custom preview",
            List.of(),
            "P-queue",
            "v1",
            null,
            null);
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .describe(() -> custom)
            .build();

    DslDescriptor desc = process.describe();
    assertThat(desc).isSameAs(custom);
    assertThat(desc.description()).isEqualTo("custom description");
    assertThat(desc.hasSideEffects()).isFalse();
  }
}
