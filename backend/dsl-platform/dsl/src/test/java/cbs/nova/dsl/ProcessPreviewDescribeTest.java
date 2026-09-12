package cbs.nova.dsl;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.registry.DefaultCompensationRegistry;
import cbs.nova.dsl.runner.DefaultProcessRunner;
import java.util.List;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

class ProcessPreviewDescribeTest {

  private final ContextFactory contextFactory = new ContextFactory();

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

    var runner = new DefaultProcessRunner(contextFactory,
            new DefaultCompensationRegistry());
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

    var runner = new DefaultProcessRunner(contextFactory,
            new DefaultCompensationRegistry());
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
  void describeExplainReturnsMarkdown() {
    var custom = DslDescriptor.builder()
            .name("OrderProc")
            .type(DslObject.DslType.PROCESS)
            .description("Describes an order flow.")
            .inputType(String.class)
            .outputType(String.class)
            .hasCompensation(false)
            .hasSideEffects(true)
            .parameters(List.of())
            .taskQueue("OrderProc-queue")
            .version("v1")
            .startToCloseTimeout(null)
            .heartbeatTimeout(null)
            .build();
    var process = Dsl.process("OrderProc")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .describe(() -> custom)
            .build();

    var markdown = process.describe().explain();

    assertThat(markdown)
            .contains("**Process** `OrderProc`")
            .contains("Describes an order flow.")
            .contains("- Input: `String`")
            .contains("- Output: `String`")
            .contains("- Side effects: yes")
            .contains("- Compensation: no");
  }
}
