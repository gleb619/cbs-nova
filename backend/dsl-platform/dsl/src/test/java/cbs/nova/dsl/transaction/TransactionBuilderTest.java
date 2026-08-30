package cbs.nova.dsl.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslObject.DslType;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TransactionBuilderTest {

  @Test
  void buildWithoutExecuteThrows() {
    var builder = Dsl.transaction("NoExec");
    assertThatThrownBy(builder::build)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("execute() is required")
            .hasMessageContaining("NoExec");
  }

  @Test
  void parametersCombinedWithInputThrows() {
    var builder = Dsl.transaction("ConflictIn")
            .execute(ctx -> cbs.nova.dsl.Result.success(null))
            .input(String.class)
            .parameters(reg -> reg.string("k"));
    assertThatThrownBy(builder::build)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("parameters()")
            .hasMessageContaining(".input()")
            .hasMessageContaining("ConflictIn");
  }

  @Test
  void parametersCombinedWithOutputThrows() {
    var builder = Dsl.transaction("ConflictOut")
            .execute(ctx -> cbs.nova.dsl.Result.success(null))
            .output(String.class)
            .parameters(reg -> reg.string("k"));
    assertThatThrownBy(builder::build)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("parameters()")
            .hasMessageContaining(".output()")
            .hasMessageContaining("ConflictOut");
  }

  @Test
  void defaultTaskQueueIsNamePlusQueueSuffix() {
    var tx = Dsl.transaction("PayTx")
            .execute(ctx -> cbs.nova.dsl.Result.success(null))
            .build();
    assertThat(tx.taskQueue()).isEqualTo("PayTx-queue");
  }

  @Test
  void defaultVersionIsV1() {
    var tx = Dsl.transaction("PayTx")
            .execute(ctx -> cbs.nova.dsl.Result.success(null))
            .build();
    assertThat(tx.version()).isEqualTo("v1");
  }

  @Test
  void defaultStartToCloseTimeoutIsThirtySeconds() {
    var tx = Dsl.transaction("PayTx")
            .execute(ctx -> cbs.nova.dsl.Result.success(null))
            .build();
    assertThat(tx.startToCloseTimeout()).isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  void fluentSettersOverrideDefaults() {
    var tx = Dsl.transaction("PayTx")
            .taskQueue("custom-queue")
            .version("v7")
            .startToCloseTimeout(Duration.ofMinutes(2))
            .execute(ctx -> cbs.nova.dsl.Result.success(null))
            .build();
    assertThat(tx.taskQueue()).isEqualTo("custom-queue");
    assertThat(tx.version()).isEqualTo("v7");
    assertThat(tx.startToCloseTimeout()).isEqualTo(Duration.ofMinutes(2));
  }

  @Test
  void buildListReturnsSingleElement() {
    var list = Dsl.transaction("PayTx")
            .execute(ctx -> cbs.nova.dsl.Result.success(null))
            .buildList();
    assertThat(list).hasSize(1);
    assertThat(list.get(0).name()).isEqualTo("PayTx");
  }

  @Test
  void builtObjectReportsTransactionType() {
    var tx = Dsl.transaction("PayTx")
            .execute(ctx -> cbs.nova.dsl.Result.success(null))
            .build();
    assertThat(tx.type()).isEqualTo(DslType.TRANSACTION);
  }

  @Test
  void effectivePreviewFallsBackToExecuteWhenPreviewNotSet() {
    var tx = Dsl.transaction("PayTx")
            .execute(ctx -> cbs.nova.dsl.Result.success("exec"))
            .build();
    assertThat(tx.effectivePreview()).isSameAs(tx.executeLogic());
  }

  @Test
  void effectivePreviewReturnsPreviewWhenSet() {
    var tx = Dsl.transaction("PayTx")
            .execute(ctx -> cbs.nova.dsl.Result.success("exec"))
            .preview(ctx -> cbs.nova.dsl.Result.success("preview"))
            .build();
    assertThat(tx.effectivePreview()).isSameAs(tx.previewLogic());
    assertThat(tx.effectivePreview()).isNotSameAs(tx.executeLogic());
  }

  @Test
  void describeUsesCustomDescriptorSupplierWhenProvided() {
    var custom = new cbs.nova.dsl.DslDescriptor(
            "PayTx", DslType.TRANSACTION, "custom-desc",
            String.class, String.class, false, false,
            "custom-preview",
            java.util.List.of(), "custom-queue", "v9", Duration.ofSeconds(1), null);
    var tx = Dsl.transaction("PayTx")
            .execute(ctx -> cbs.nova.dsl.Result.success(null))
            .describe(() -> custom)
            .build();
    assertThat(tx.describe()).isSameAs(custom);
  }

  @Test
  void describeBuildsDefaultDescriptorWhenSupplierAbsent() {
    var tx = Dsl.transaction("PayTx")
            .input(String.class)
            .execute(ctx -> cbs.nova.dsl.Result.success(null))
            .compensation(ctx -> cbs.nova.dsl.Result.success(null))
            .build();
    var desc = tx.describe();
    assertThat(desc.name()).isEqualTo("PayTx");
    assertThat(desc.type()).isEqualTo(DslType.TRANSACTION);
    assertThat(desc.hasCompensation()).isTrue();
    assertThat(desc.inputType()).isEqualTo(String.class);
    assertThat(desc.taskQueue()).isEqualTo("PayTx-queue");
    assertThat(desc.version()).isEqualTo("v1");
  }

  @Test
  void describeReportsNoCompensationWhenAbsent() {
    var tx = Dsl.transaction("PayTx")
            .execute(ctx -> cbs.nova.dsl.Result.success(null))
            .build();
    assertThat(tx.describe().hasCompensation()).isFalse();
  }
}
