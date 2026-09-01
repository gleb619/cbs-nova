package cbs.nova.dsl.function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.Result;
import java.util.List;
import org.junit.jupiter.api.Test;

class FunctionBuilderTest {

  @Test
  void buildWithoutExecuteThrows() {
    var builder = Dsl.function("NoExecFn");
    assertThatThrownBy(builder::build)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("execute() is required")
            .hasMessageContaining("NoExecFn");
  }

  @Test
  void parametersCombinedWithInputThrows() {
    var builder = Dsl.function("ConflictInFn")
            .execute(ctx -> Result.success(null))
            .input(String.class)
            .parameters(reg -> reg.string("k"));
    assertThatThrownBy(builder::build)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("parameters()")
            .hasMessageContaining(".input()")
            .hasMessageContaining("ConflictInFn");
  }

  @Test
  void parametersCombinedWithOutputThrows() {
    var builder = Dsl.function("ConflictOutFn")
            .execute(ctx -> Result.success(null))
            .output(String.class)
            .parameters(reg -> reg.string("k"));
    assertThatThrownBy(builder::build)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("parameters()")
            .hasMessageContaining(".output()")
            .hasMessageContaining("ConflictOutFn");
  }

  @Test
  void fluentInputOutputRetainedOnBuiltObject() {
    var fn = Dsl.function("EchoFn")
            .input(String.class)
            .output(Integer.class)
            .execute(ctx -> Result.success(42))
            .build();
    assertThat(fn.name()).isEqualTo("EchoFn");
    assertThat(fn.parameters()).isNull();
  }

  @Test
  void parametersStoredOnBuiltObject() {
    var fn = Dsl.function("MappedFn")
            .parameters(reg -> {
              reg.string("a");
              reg.number("b");
            })
            .execute(ctx -> Result.success(null))
            .build();
    assertThat(fn.parameters()).hasSize(2);
    assertThat(fn.parameters().get(0).name()).isEqualTo("a");
    assertThat(fn.parameters().get(1).name()).isEqualTo("b");
  }

  @Test
  void effectivePreviewFallsBackToExecuteWhenPreviewNotSet() {
    var fn = Dsl.function("NoPrevFn")
            .execute(ctx -> Result.success("exec"))
            .build();
    assertThat(fn.effectivePreview()).isSameAs(fn.executeLogic());
    assertThat(fn.previewLogic()).isNull();
  }

  @Test
  void effectivePreviewReturnsPreviewWhenSet() {
    var fn = Dsl.function("WithPrevFn")
            .execute(ctx -> Result.success("exec"))
            .preview(ctx -> Result.success("prev"))
            .build();
    assertThat(fn.effectivePreview()).isSameAs(fn.previewLogic());
    assertThat(fn.effectivePreview()).isNotSameAs(fn.executeLogic());
  }

  @Test
  void builtObjectReportsFunctionType() {
    var fn = Dsl.function("TypedFn")
            .execute(ctx -> Result.success(null))
            .build();
    assertThat(fn.type()).isEqualTo(DslType.FUNCTION);
  }

  @Test
  void buildListReturnsSingleElementWrappedInList() {
    var list = Dsl.function("ListedFn")
            .execute(ctx -> Result.success(null))
            .buildList();
    assertThat(list).hasSize(1);
    assertThat(list.get(0).name()).isEqualTo("ListedFn");
    assertThat(list.get(0)).isInstanceOf(FunctionDslObject.class);
  }

  @Test
  void describeBuildsDefaultDescriptorWhenSupplierAbsent() {
    var fn = Dsl.function("DefaultDescFn")
            .parameters(reg -> reg.string("k"))
            .execute(ctx -> Result.success(null))
            .build();
    var desc = fn.describe();
    assertThat(desc.name()).isEqualTo("DefaultDescFn");
    assertThat(desc.type()).isEqualTo(DslType.FUNCTION);
    assertThat(desc.previewBehavior()).isEqualTo("delegates to execute");
    assertThat(desc.parameters()).hasSize(1);
    assertThat(desc.parameters().get(0).name()).isEqualTo("k");
  }

  @Test
  void describeUsesCustomDescriptorSupplierWhenProvided() {
    var custom = new DslDescriptor(
            "CustomDescFn", DslType.FUNCTION, "custom-desc",
            String.class, Integer.class, false, false,
            "custom-preview",
            List.of(), null, null, null, null);
    var fn = Dsl.function("CustomDescFn")
            .execute(ctx -> Result.success(null))
            .describe(() -> custom)
            .build();
    assertThat(fn.describe()).isSameAs(custom);
  }
}
