package cbs.nova.dsl.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.CompensationContext;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.config.DslConstants;
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
  void effectiveExplainFallsBackToDescriptorReportWhenExplainNotSet() {
    var process = Dsl.process("NoExplainProc")
            .execute(ctx -> Result.success("exec"))
            .build();
    var contextFactory = new ContextFactory();
    var ctx = new ProcessRichContext<>(
            contextFactory.of("body", ExecutionMode.EXPLAIN, "run-explain"), contextFactory);

    var result = process.explainLogic().apply(ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isNotNull();
    assertThat(result.value().name()).isEqualTo("NoExplainProc");
    assertThat(result.value().description()).contains("**Process** `NoExplainProc`");
    assertThat(result.value().mermaid()).isEmpty();
  }

  @Test
  void effectiveExplainReturnsExplainWhenSet() {
    var report = new ExplainReport("WithExplainProc", "explain", "");
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
    var contextFactory = new ContextFactory();
    var ctx = new ProcessRichContext<>(
            contextFactory.of("body", ExecutionMode.EXPLAIN, "run-doc"), contextFactory);

    var result = process.explainLogic().apply(ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().name()).isEqualTo("DocProc");
    assertThat(result.value().description()).contains("# Builder Sample");
  }

  @Test
  void explainViaAcceptsPrefixedResourcePath() {
    var process = Dsl.process("DocProcPrefixed")
            .execute(ctx -> Result.success("exec"))
            .explainVia("/explain/builder-sample.md")
            .build();
    var contextFactory = new ContextFactory();
    var ctx = new ProcessRichContext<>(
            contextFactory.of("body", ExecutionMode.EXPLAIN, "run-doc-prefixed"), contextFactory);

    var result = process.explainLogic().apply(ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().description()).contains("# Builder Sample");
  }

  @Test
  void explainViaTruncatesMarkdownToMetadataBudget() {
    var process = Dsl.process("DocProcBudget")
            .execute(ctx -> Result.success("exec"))
            .explainVia("explain/builder-sample.md")
            .build();
    var contextFactory = new ContextFactory();
    var ctx = new ProcessRichContext<>(
            contextFactory.of("body",
                    Map.of(Constants.EXPLAIN_BUDGET_CHARS_KEY, 8),
                    ExecutionMode.EXPLAIN, "run-doc-budget"),
            contextFactory);

    var result = process.explainLogic().apply(ctx);

    assertThat(result.value().description()).hasSizeLessThanOrEqualTo(8);
  }

  @Test
  void explainViaIsLazyWhenResourceMissing() {
    var process = Dsl.process("DocProcMissing")
            .execute(ctx -> Result.success("exec"))
            .explainVia("missing.md")
            .build();
    var contextFactory = new ContextFactory();
    var ctx = new ProcessRichContext<>(
            contextFactory.of("body", ExecutionMode.EXPLAIN, "run-doc-missing"), contextFactory);

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
}
