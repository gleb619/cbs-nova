package cbs.nova.dsl.function;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.explain.ExplainResourceProvider;
import cbs.nova.dsl.model.ExplainReport;
import java.util.List;
import java.util.Map;
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
    assertThat(fn.parameters()).isEmpty();
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
    assertThat(fn.previewLogic()).isSameAs(fn.executeLogic());
    assertThat(fn.previewLogic()).isSameAs(fn.executeLogic());
  }

  @Test
  void effectivePreviewReturnsPreviewWhenSet() {
    var fn = Dsl.function("WithPrevFn")
            .execute(ctx -> Result.success("exec"))
            .preview(ctx -> Result.success("prev"))
            .build();
    assertThat(fn.previewLogic()).isSameAs(fn.previewLogic());
    assertThat(fn.previewLogic()).isNotSameAs(fn.executeLogic());
  }

  @Test
  void effectiveExplainFallsBackToEmptyMarkdownWhenNoResourceRegistered() {
    var fn = Dsl.function("NoExplainFn")
            .execute(ctx -> Result.success("exec"))
            .build();
    var ctx = new FunctionRichContext<>(
            SimpleContext.builder().body("body").mode(ExecutionMode.EXPLAIN).runId("run-explain")
                    .build());

    var result = fn.explainLogic().apply(ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isNotNull();
    assertThat(result.value().name()).isEqualTo("NoExplainFn");
    assertThat(result.value().markdown()).isEqualTo(Constants.EMPTY_MARKDOWN);
    assertThat(result.value().description()).isEqualTo(Constants.EMPTY_MARKDOWN);
  }

  @Test
  void effectiveExplainReturnsExplainWhenSet() {
    var report = ExplainReport.builder().name("WithExplainFn").description("explain").markdown("")
            .build();
    var fn = Dsl.function("WithExplainFn")
            .execute(ctx -> Result.success("exec"))
            .explain(ctx -> Result.success(report))
            .build();
    assertThat(fn.explainLogic()).isSameAs(fn.explainLogic());
    assertThat(fn.explainLogic()).isNotSameAs(fn.executeLogic());
  }

  @Test
  void explainViaLoadsResourceMarkdown() {
    var fn = Dsl.function("DocFn")
            .execute(ctx -> Result.success("exec"))
            .explainVia("builder-sample.md")
            .build();
    var ctx = new FunctionRichContext<>(
            SimpleContext.builder().body("body").mode(ExecutionMode.EXPLAIN).runId("run-doc")
                    .build());

    var result = fn.explainLogic().apply(ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().name()).isEqualTo("DocFn");
    assertThat(result.value().markdown()).contains("# Builder Sample");
  }

  @Test
  void explainViaAcceptsPrefixedResourcePath() {
    var fn = Dsl.function("DocFnPrefixed")
            .execute(ctx -> Result.success("exec"))
            .explainVia("explain/builder-sample.md")
            .build();
    var ctx = new FunctionRichContext<>(
            SimpleContext.builder().body("body").mode(ExecutionMode.EXPLAIN)
                    .runId("run-doc-prefixed").build());

    var result = fn.explainLogic().apply(ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().markdown()).contains("# Builder Sample");
  }

  @Test
  void explainViaLoadsFullMarkdownRegardlessOfMetadataBudget() {
    var fn = Dsl.function("DocFnBudget")
            .execute(ctx -> Result.success("exec"))
            .explainVia("builder-sample.md")
            .build();
    var ctx = new FunctionRichContext<>(
            SimpleContext.builder().body("body")
                    .metadata(Map.of(Constants.EXPLAIN_BUDGET_CHARS_KEY, 10))
                    .mode(ExecutionMode.EXPLAIN).runId("run-doc-budget").build());

    var result = fn.explainLogic().apply(ctx);

    assertThat(result.value().markdown())
            .contains("# Builder Sample")
            .hasSizeGreaterThan(10);
  }

  @Test
  void explainViaIsLazyWhenResourceMissing() {
    var fn = Dsl.function("DocFnMissing")
            .execute(ctx -> Result.success("exec"))
            .explainVia("does-not-exist.md")
            .build();
    var ctx = new FunctionRichContext<>(
            SimpleContext.builder().body("body").mode(ExecutionMode.EXPLAIN)
                    .runId("run-doc-missing").build());

    var result = fn.explainLogic().apply(ctx);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("classpath: explain/does-not-exist.md");
  }

  @Test
  void explainOverridesExplainVia() {
    var fn = Dsl.function("DocFnCleared")
            .execute(ctx -> Result.success("exec"))
            .explainVia("builder-sample.md")
            .explain(ctx -> Result.success(ExplainReport.builder().name("DocFnCleared")
                    .description("code").markdown("").build()))
            .build();
    var ctx = new FunctionRichContext<>(
            SimpleContext.builder().body("body").mode(ExecutionMode.EXPLAIN)
                    .runId("run-doc-cleared").build());

    var result = fn.explainLogic().apply(ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().description()).isEqualTo("code");
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
    DslDescriptor desc = fn.descriptor();
    assertThat(desc.name()).isEqualTo("DefaultDescFn");
    assertThat(desc.type()).isEqualTo(DslType.FUNCTION);
    assertThat(desc.parameters()).hasSize(1);
    assertThat(desc.parameters().get(0).name()).isEqualTo("k");
  }

  @Test
  void describeUsesCustomDescriptorSupplierWhenProvided() {
    var objectDescriptor = FunctionDescriptor.builder()
            .name("CustomDescFn")
            .description("custom-desc")
            .inputType(String.class)
            .outputType(Integer.class)
            .build();
    var custom = DslDescriptor.builder()
            .objectDescriptor(objectDescriptor)
            .parameters(List.of())
            .taskQueue(null)
            .version(null)
            .startToCloseTimeout(null)
            .heartbeatTimeout(null)
            .build();
    var fn = Dsl.function("CustomDescFn")
            .execute(ctx -> Result.success(null))
            .describe(() -> custom)
            .build();
    assertThat(fn.descriptor()).isSameAs(custom);
  }

  @Test
  void implementsObjectBuilderInterface() {
    assertThat(Dsl.function("AnyFn")).isInstanceOf(cbs.nova.dsl.model.ObjectBuilder.class);
  }

  @Test
  void defaultExplainUsesExplainResourceByMetadataName() {
    var gm = GlobalManager.globalManager();
    gm.registerExplainResource(stubProvider(
            "LookupFn", "by-name", "lookup-fn.md", "# By Name Body"));
    try {
      var fn = Dsl.function("LookupFn")
              .execute(ctx -> Result.success("exec"))
              .build();
      var ctx = new FunctionRichContext<>(
              SimpleContext.builder().body("body").mode(ExecutionMode.EXPLAIN)
                      .runId("run-lookup-name").build());

      var result = fn.explainLogic().apply(ctx);

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.value().markdown()).isEqualTo("# By Name Body");
    } finally {
      gm.resetForTests();
    }
  }

  @Test
  void defaultExplainUsesExplainResourceByFilename() {
    var gm = GlobalManager.globalManager();
    gm.registerExplainResource(stubProvider(
            "BatchProcessing", "sums", "batch-processing.md", "# Filename Body"));
    try {
      var fn = Dsl.function("BatchProcessing")
              .execute(ctx -> Result.success("exec"))
              .build();
      var ctx = new FunctionRichContext<>(
              SimpleContext.builder().body("body").mode(ExecutionMode.EXPLAIN)
                      .runId("run-lookup-file").build());

      var result = fn.explainLogic().apply(ctx);

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.value().markdown()).isEqualTo("# Filename Body");
    } finally {
      gm.resetForTests();
    }
  }

  @Test
  void defaultExplainPrefersMetadataNameOverFilename() {
    var gm = GlobalManager.globalManager();
    gm.registerExplainResource(stubProvider(
            "PreferFn", "by-name", "prefer-fn.md", "# Name Wins"));
    gm.registerExplainResource(stubProvider(
            "PreferFnFile", "by-file", "preferfn.md", "# File Loses"));
    try {
      var fn = Dsl.function("PreferFn")
              .execute(ctx -> Result.success("exec"))
              .build();
      var ctx = new FunctionRichContext<>(
              SimpleContext.builder().body("body").mode(ExecutionMode.EXPLAIN)
                      .runId("run-lookup-prefer").build());

      var result = fn.explainLogic().apply(ctx);

      assertThat(result.value().markdown()).isEqualTo("# Name Wins");
    } finally {
      gm.resetForTests();
    }
  }

  @Test
  void defaultExplainFallsBackToEmptyMarkdownWhenResourceMissing() {
    var fn = Dsl.function("MissingFn")
            .execute(ctx -> Result.success("exec"))
            .build();
    var ctx = new FunctionRichContext<>(
            SimpleContext.builder().body("body").mode(ExecutionMode.EXPLAIN).runId("run-fallback")
                    .build());

    var result = fn.explainLogic().apply(ctx);

    assertThat(result.value().markdown()).isEqualTo(Constants.EMPTY_MARKDOWN);
    assertThat(result.value().description()).isEqualTo(Constants.EMPTY_MARKDOWN);
  }

  private static ExplainResourceProvider stubProvider(
          String name, String description, String filename, String content) {
    return new ExplainResourceProvider() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public String description() {
        return description;
      }

      @Override
      public String filename() {
        return filename;
      }

      @Override
      public String content() {
        return content;
      }
    };
  }
}
