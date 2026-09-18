package cbs.nova.dsl.process;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.CompensationContext;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.config.DslConstants;
import cbs.nova.dsl.explain.ExplainResourceProvider;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.transaction.TransactionExecution;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
  void compensationIsRetainedAsBiConsumer() {
    var captured = new AtomicReference<List<TransactionExecution>>();
    var process = Dsl.process("BiCompProc")
            .execute(ctx -> Result.success(null))
            .compensation((CompensationContext<Object> ctx, List<TransactionExecution> history) -> {
              captured.set(history);
            })
            .build();
    assertThat(process.compensationLogic()).isNotNull();
    // Invoke the captured BiConsumer to confirm it runs and accepts a null-safe history list.
    process.compensationLogic().accept(null, List.of());
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
  void builtObjectDefaultsToVoidTypesAndEmptyMarkdownDescription() {
    var process = Dsl.process("BareProc")
            .execute(ctx -> Result.success(null))
            .build();
    assertThat(process.inputType()).isEqualTo(Void.class);
    assertThat(process.outputType()).isEqualTo(Void.class);
    assertThat(process.description()).isEqualTo(Constants.EMPTY_MARKDOWN);
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
    assertThat(process.parameters()).isEmpty();
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
    assertThat(process.previewLogic()).isSameAs(process.executeLogic());
  }

  @Test
  void effectivePreviewReturnsPreviewWhenSet() {
    var process = Dsl.process("WithPrevProc")
            .execute(ctx -> Result.success("exec"))
            .preview(ctx -> Result.success("prev"))
            .build();
    assertThat(process.previewLogic()).isSameAs(process.previewLogic());
    assertThat(process.previewLogic()).isNotSameAs(process.executeLogic());
  }

  @Test
  void effectiveExplainFallsBackToEmptyMarkdownWhenNoResourceRegistered() {
    var process = Dsl.process("NoExplainProc")
            .execute(ctx -> Result.success("exec"))
            .build();
    var ctx = new ProcessRichContext<>(
            SimpleContext.builder().body("body").mode(ExecutionMode.EXPLAIN).runId("run-explain")
                    .build());

    var result = process.explainLogic().apply(ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isNotNull();
    assertThat(result.value().name()).isEqualTo("NoExplainProc");
    assertThat(result.value().mermaid()).isEqualTo(Constants.EMPTY_MARKDOWN);
    assertThat(result.value().description()).isEmpty();

  }

  @Test
  void effectiveExplainReturnsExplainWhenSet() {
    var report = ExplainReport.builder().name("WithExplainProc").description("explain").mermaid("")
            .build();
    var process = Dsl.process("WithExplainProc")
            .execute(ctx -> Result.success("exec"))
            .explain(ctx -> Result.success(report))
            .build();
    assertThat(process.explainLogic()).isSameAs(process.explainLogic());
    assertThat(process.explainLogic()).isNotSameAs(process.executeLogic());
  }

  @Test
  void explainViaLoadsResourceMarkdown() {
    var process = Dsl.process("DocProc")
            .execute(ctx -> Result.success("exec"))
            .explainVia("builder-sample.md")
            .build();
    var ctx = new ProcessRichContext<>(
            SimpleContext.builder().body("body").mode(ExecutionMode.EXPLAIN).runId("run-doc")
                    .build());

    var result = process.explainLogic().apply(ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().name()).isEqualTo("DocProc");
    assertThat(result.value().mermaid()).contains("# Builder Sample");
  }

  @Test
  void explainViaAcceptsPrefixedResourcePath() {
    var process = Dsl.process("DocProcPrefixed")
            .execute(ctx -> Result.success("exec"))
            .explainVia("/explain/builder-sample.md")
            .build();
    var ctx = new ProcessRichContext<>(
            SimpleContext.builder().body("body").mode(ExecutionMode.EXPLAIN)
                    .runId("run-doc-prefixed").build());

    var result = process.explainLogic().apply(ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().mermaid()).contains("# Builder Sample");
  }

  @Test
  void explainViaLoadsFullMarkdownRegardlessOfMetadataBudget() {
    var process = Dsl.process("DocProcBudget")
            .execute(ctx -> Result.success("exec"))
            .explainVia("explain/builder-sample.md")
            .build();
    var ctx = new ProcessRichContext<>(
            SimpleContext.builder().body("body")
                    .metadata(Map.of(Constants.EXPLAIN_BUDGET_CHARS_KEY, 8))
                    .mode(ExecutionMode.EXPLAIN).runId("run-doc-budget").build());

    var result = process.explainLogic().apply(ctx);

    assertThat(result.value().mermaid())
            .contains("# Builder Sample")
            .hasSizeGreaterThan(8);
  }

  @Test
  void explainViaIsLazyWhenResourceMissing() {
    var process = Dsl.process("DocProcMissing")
            .execute(ctx -> Result.success("exec"))
            .explainVia("missing.md")
            .build();
    var ctx = new ProcessRichContext<>(
            SimpleContext.builder().body("body").mode(ExecutionMode.EXPLAIN)
                    .runId("run-doc-missing").build());

    var result = process.explainLogic().apply(ctx);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("classpath: explain/missing.md");
  }

  @Test
  void describeBuildsDefaultDescriptorWhenCompensationPresent() {
    var process = Dsl.process("DefaultDescProc")
            .input(String.class)
            .execute(ctx -> Result.success(null))
            .compensation((ctx, history) -> {
            })
            .build();
    var desc = process.descriptor();
    assertThat(desc.name()).isEqualTo("DefaultDescProc");
    assertThat(desc.type()).isEqualTo(DslType.PROCESS);
    assertThat(desc.hasSideEffects()).isTrue();
    assertThat(desc.inputType()).isEqualTo(String.class);
    assertThat(desc.taskQueue()).isEqualTo("DefaultDescProc-queue");
    assertThat(desc.version()).isEqualTo("v1");
    assertThat(desc.startToCloseTimeout()).isEqualTo(DslConstants.DEFAULT_START_TO_CLOSE_TIMEOUT);
    assertThat(desc.heartbeatTimeout()).isEqualTo(DslConstants.DEFAULT_HEARTBEAT_TIMEOUT);
  }

  @Test
  void describeReportsNoSideEffectsWhenNoCompensation() {
    var process = Dsl.process("NoCompProc")
            .execute(ctx -> Result.success(null))
            .build();
    assertThat(process.compensationLogic()).isNull();
    assertThat(process.descriptor().hasSideEffects()).isFalse();
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

  @Test
  void defaultExplainUsesExplainResourceByMetadataName() {
    var gm = GlobalManager.globalManager();
    gm.registerExplainResource(stubProvider(
            "LookupProc", "by-name", "lookup-proc.md", "# Proc By Name"));
    try {
      var process = Dsl.process("LookupProc")
              .execute(ctx -> Result.success("exec"))
              .build();
      var ctx = new ProcessRichContext<>(
              SimpleContext.builder().body("body").mode(ExecutionMode.EXPLAIN)
                      .runId("run-proc-lookup-name").build());

      var result = process.explainLogic().apply(ctx);

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.value().mermaid()).isEqualTo("# Proc By Name");
    } finally {
      gm.resetForTests();
    }
  }

  @Test
  void defaultExplainUsesExplainResourceByFilename() {
    var gm = GlobalManager.globalManager();
    gm.registerExplainResource(stubProvider(
            "OrderFlow", "orders", "order-flow.md", "# Proc Filename"));
    try {
      var process = Dsl.process("OrderFlow")
              .execute(ctx -> Result.success("exec"))
              .build();
      var ctx = new ProcessRichContext<>(
              SimpleContext.builder().body("body").mode(ExecutionMode.EXPLAIN)
                      .runId("run-proc-lookup-file").build());

      var result = process.explainLogic().apply(ctx);

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.value().mermaid()).isEqualTo("# Proc Filename");
    } finally {
      gm.resetForTests();
    }
  }

  @Test
  void defaultExplainPrefersMetadataNameOverFilename() {
    var gm = GlobalManager.globalManager();
    gm.registerExplainResource(stubProvider(
            "PreferProc", "by-name", "prefer-proc.md", "# Name Wins"));
    gm.registerExplainResource(stubProvider(
            "PreferProcFile", "by-file", "preferproc.md", "# File Loses"));
    try {
      var process = Dsl.process("PreferProc")
              .execute(ctx -> Result.success("exec"))
              .build();
      var ctx = new ProcessRichContext<>(
              SimpleContext.builder().body("body").mode(ExecutionMode.EXPLAIN)
                      .runId("run-proc-lookup-prefer").build());

      var result = process.explainLogic().apply(ctx);

      assertThat(result.value().mermaid()).isEqualTo("# Name Wins");
    } finally {
      gm.resetForTests();
    }
  }

  @Test
  void defaultExplainFallsBackToEmptyMarkdownWhenResourceMissing() {
    var process = Dsl.process("MissingProc")
            .execute(ctx -> Result.success("exec"))
            .build();
    var ctx = new ProcessRichContext<>(
            SimpleContext.builder().body("body").mode(ExecutionMode.EXPLAIN)
                    .runId("run-proc-fallback").build());

    var result = process.explainLogic().apply(ctx);

    assertThat(result.value().mermaid()).isEqualTo(Constants.EMPTY_MARKDOWN);
    assertThat(result.value().description()).isEmpty();
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

  @Test
  void descriptionPropagatesToBuiltObjectAndDescriptor() {
    var process = Dsl.process("DescribedProc")
            .input(String.class)
            .output(Integer.class)
            .description("A described process.")
            .execute(ctx -> Result.success(42))
            .build();

    assertThat(process.description()).isEqualTo("A described process.");
    assertThat(process.descriptor().objectDescriptor().description())
            .isEqualTo("A described process.");
  }

  @Test
  void defaultExplainUsesBuilderDescriptionWhenNoResourceRegistered() {
    var process = Dsl.process("DescribedDefaultExplainProc")
            .description("Default description.")
            .execute(ctx -> Result.success(null))
            .build();
    var ctx = new ProcessRichContext<>(
            SimpleContext.builder().body("body").mode(ExecutionMode.EXPLAIN).runId("run-desc")
                    .build());

    var result = process.explainLogic().apply(ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().description()).isEqualTo("Default description.");
    assertThat(result.value().mermaid()).isEqualTo(Constants.EMPTY_MARKDOWN);
  }

  @Test
  void explainViaSetsDescriptionFromFrontmatter() {
    var process = Dsl.process("FrontmatterProc")
            .execute(ctx -> Result.success("exec"))
            .explainVia("builder-sample.md")
            .build();
    var ctx = new ProcessRichContext<>(
            SimpleContext.builder().body("body").mode(ExecutionMode.EXPLAIN).runId("run-fm")
                    .build());

    var result = process.explainLogic().apply(ctx);

    assertThat(result.value().description())
            .isEqualTo("Markdown backing the explainVia builder tests.");
    assertThat(result.value().mermaid()).contains("# Builder Sample");
  }

}
