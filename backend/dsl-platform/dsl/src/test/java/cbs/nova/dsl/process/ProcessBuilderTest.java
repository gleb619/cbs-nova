package cbs.nova.dsl.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.CompensationContext;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.transaction.TransactionExecution;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessBuilderTest {

  @Test
  void buildWithoutExecuteThrows() {
    var builder = Dsl.process("NoExecProc");
    assertThatThrownBy(builder::build)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("execute() is required")
            .hasMessageContaining("NoExecProc");
  }

  @Test
  void parametersCombinedWithInputThrows() {
    var builder = Dsl.process("ConflictInProc")
            .execute(ctx -> Result.success(null))
            .input(String.class)
            .parameters(reg -> reg.string("k"));
    assertThatThrownBy(builder::build)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("parameters()")
            .hasMessageContaining(".input()")
            .hasMessageContaining("ConflictInProc");
  }

  @Test
  void parametersCombinedWithOutputThrows() {
    var builder = Dsl.process("ConflictOutProc")
            .execute(ctx -> Result.success(null))
            .output(String.class)
            .parameters(reg -> reg.string("k"));
    assertThatThrownBy(builder::build)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("parameters()")
            .hasMessageContaining(".output()")
            .hasMessageContaining("ConflictOutProc");
  }

  @Test
  void defaultTaskQueueIsNamePlusQueueSuffix() {
    var process = Dsl.process("OrderProc")
            .execute(ctx -> Result.success(null))
            .build();
    assertThat(process.taskQueue()).isEqualTo("OrderProc-queue");
  }

  @Test
  void defaultVersionIsV1() {
    var process = Dsl.process("OrderProc")
            .execute(ctx -> Result.success(null))
            .build();
    assertThat(process.version()).isEqualTo("v1");
  }

  @Test
  void fluentSettersOverrideDefaults() {
    var process = Dsl.process("OrderProc")
            .taskQueue("custom-queue")
            .version("v7")
            .execute(ctx -> Result.success(null))
            .build();
    assertThat(process.taskQueue()).isEqualTo("custom-queue");
    assertThat(process.version()).isEqualTo("v7");
  }

  @Test
  void transactionsListIsRetainedOnBuiltObject() {
    var process = Dsl.process("TxProc")
            .transactions(List.of("TxA", "TxB", "TxC"))
            .execute(ctx -> Result.success(null))
            .build();
    assertThat(process.transactionRefs()).containsExactly("TxA", "TxB", "TxC");
  }

  @Test
  void defaultTransactionsListIsEmpty() {
    var process = Dsl.process("NoTxProc")
            .execute(ctx -> Result.success(null))
            .build();
    assertThat(process.transactionRefs()).isEmpty();
  }

  @Test
  void functionCompensationOverloadIsRetained() {
    var process = Dsl.process("FuncCompProc")
            .execute(ctx -> Result.success(null))
            .compensation((CompensationContext<Object> ctx) -> Result.success(null))
            .build();
    assertThat(process.compensationLogic()).isNotNull();
    assertThat(process.userCompensationHandler()).isNull();
  }

  @Test
  void biConsumerCompensationOverloadIsRetained() {
    var captured = new java.util.concurrent.atomic.AtomicReference<List<TransactionExecution>>();
    var process = Dsl.process("BiCompProc")
            .execute(ctx -> Result.success(null))
            .compensation((CompensationContext<Object> ctx, List<TransactionExecution> history) -> {
              captured.set(history);
            })
            .build();
    assertThat(process.userCompensationHandler()).isNotNull();
    assertThat(process.compensationLogic()).isNull();
    // Invoke the captured BiConsumer to confirm it runs and accepts a null-safe history list.
    process.userCompensationHandler().accept(null, List.of());
    assertThat(captured.get()).isEmpty();
  }

  @Test
  void builtObjectReportsProcessType() {
    var process = Dsl.process("TypedProc")
            .execute(ctx -> Result.success(null))
            .build();
    assertThat(process.type()).isEqualTo(DslType.PROCESS);
    assertThat(process.name()).isEqualTo("TypedProc");
  }

  @Test
  void fluentInputOutputRetainedOnBuiltObject() {
    var process = Dsl.process("EchoProc")
            .input(String.class)
            .output(Integer.class)
            .execute(ctx -> Result.success(42))
            .build();
    assertThat(process.inputType()).isEqualTo(String.class);
    assertThat(process.outputType()).isEqualTo(Integer.class);
    assertThat(process.parameters()).isNull();
  }

  @Test
  void parametersStoredOnBuiltObject() {
    var process = Dsl.process("MappedProc")
            .parameters(reg -> {
              reg.string("a");
              reg.number("b");
            })
            .execute(ctx -> Result.success(null))
            .build();
    assertThat(process.parameters()).hasSize(2);
    assertThat(process.parameters().get(0).name()).isEqualTo("a");
    assertThat(process.parameters().get(1).name()).isEqualTo("b");
  }

  @Test
  void effectivePreviewFallsBackToExecuteWhenPreviewNotSet() {
    var process = Dsl.process("NoPrevProc")
            .execute(ctx -> Result.success("exec"))
            .build();
    assertThat(process.effectivePreview()).isSameAs(process.executeLogic());
  }

  @Test
  void effectivePreviewReturnsPreviewWhenSet() {
    var process = Dsl.process("WithPrevProc")
            .execute(ctx -> Result.success("exec"))
            .preview(ctx -> Result.success("prev"))
            .build();
    assertThat(process.effectivePreview()).isSameAs(process.previewLogic());
    assertThat(process.effectivePreview()).isNotSameAs(process.executeLogic());
  }

  @Test
  void describeBuildsDefaultDescriptorWhenSupplierAbsent() {
    var process = Dsl.process("DefaultDescProc")
            .input(String.class)
            .execute(ctx -> Result.success(null))
            .compensation(ctx -> Result.success(null))
            .build();
    var desc = process.describe();
    assertThat(desc.name()).isEqualTo("DefaultDescProc");
    assertThat(desc.type()).isEqualTo(DslType.PROCESS);
    assertThat(desc.hasCompensation()).isTrue();
    assertThat(desc.inputType()).isEqualTo(String.class);
    assertThat(desc.taskQueue()).isEqualTo("DefaultDescProc-queue");
    assertThat(desc.version()).isEqualTo("v1");
  }

  @Test
  void describeReportsNoCompensationWhenAbsent() {
    var process = Dsl.process("NoCompProc")
            .execute(ctx -> Result.success(null))
            .build();
    assertThat(process.describe().hasCompensation()).isFalse();
  }

  @Test
  void describeUsesCustomDescriptorSupplierWhenProvided() {
    var custom = new cbs.nova.dsl.DslDescriptor(
            "CustomProc", DslType.PROCESS, "custom-desc",
            String.class, String.class, true, false,
            "custom-preview",
            List.of(), "custom-queue", "v9", null, null);
    var process = Dsl.process("CustomProc")
            .execute(ctx -> Result.success(null))
            .describe(() -> custom)
            .build();
    assertThat(process.describe()).isSameAs(custom);
  }

  @Test
  void buildListReturnsSingleElementWrappedInList() {
    var list = Dsl.process("ListedProc")
            .execute(ctx -> Result.success(null))
            .buildList();
    assertThat(list).hasSize(1);
    assertThat(list.get(0).name()).isEqualTo("ListedProc");
    assertThat(list.get(0)).isInstanceOf(ProcessDslObject.class);
  }
}
