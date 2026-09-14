package cbs.nova.dsl.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.explain.DescriptorMarkdown;
import cbs.nova.dsl.explain.ExplainResourceProvider;
import cbs.nova.dsl.model.ExplainReport;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
            .execute(ctx -> Result.success(null))
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
            .execute(ctx -> Result.success(null))
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
            .execute(ctx -> Result.success(null))
            .build();
    assertThat(tx.taskQueue()).isEqualTo("PayTx-queue");
  }

  @Test
  void defaultVersionIsV1() {
    var tx = Dsl.transaction("PayTx")
            .execute(ctx -> Result.success(null))
            .build();
    assertThat(tx.version()).isEqualTo("v1");
  }

  @Test
  void defaultStartToCloseTimeoutIsThirtySeconds() {
    var tx = Dsl.transaction("PayTx")
            .execute(ctx -> Result.success(null))
            .build();
    assertThat(tx.startToCloseTimeout()).isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  void fluentSettersOverrideDefaults() {
    var tx = Dsl.transaction("PayTx")
            .taskQueue("custom-queue")
            .version("v7")
            .startToCloseTimeout(Duration.ofMinutes(2))
            .execute(ctx -> Result.success(null))
            .build();
    assertThat(tx.taskQueue()).isEqualTo("custom-queue");
    assertThat(tx.version()).isEqualTo("v7");
    assertThat(tx.startToCloseTimeout()).isEqualTo(Duration.ofMinutes(2));
  }

  @Test
  void buildListReturnsSingleElement() {
    var list = Dsl.transaction("PayTx")
            .execute(ctx -> Result.success(null))
            .buildList();
    assertThat(list).hasSize(1);
    assertThat(list.get(0).name()).isEqualTo("PayTx");
  }

  @Test
  void builtObjectReportsTransactionType() {
    var tx = Dsl.transaction("PayTx")
            .execute(ctx -> Result.success(null))
            .build();
    assertThat(tx.type()).isEqualTo(DslType.TRANSACTION);
  }

  @Test
  void effectivePreviewFallsBackToExecuteWhenPreviewNotSet() {
    var tx = Dsl.transaction("PayTx")
            .execute(ctx -> Result.success("exec"))
            .build();
    assertThat(tx.effectivePreview()).isSameAs(tx.executeLogic());
  }

  @Test
  void effectivePreviewReturnsPreviewWhenSet() {
    var tx = Dsl.transaction("PayTx")
            .execute(ctx -> Result.success("exec"))
            .preview(ctx -> Result.success("preview"))
            .build();
    assertThat(tx.effectivePreview()).isSameAs(tx.previewLogic());
    assertThat(tx.effectivePreview()).isNotSameAs(tx.executeLogic());
  }

  @Test
  void effectiveExplainFallsBackToEmptyMarkdownWhenNoResourceRegistered() {
    var tx = Dsl.transaction("NoExplainTx")
            .execute(ctx -> Result.success("exec"))
            .build();
    var contextFactory = new ContextFactory();
    var ctx = new TransactionRichContext<>(
            contextFactory.of("body", ExecutionMode.EXPLAIN, "run-explain"), contextFactory);

    var result = tx.effectiveExplain().apply(ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isNotNull();
    assertThat(result.value().name()).isEqualTo("NoExplainTx");
    assertThat(result.value().mermaid()).isEqualTo(Constants.EMPTY_MARKDOWN);
    assertThat(result.value().description()).isEmpty();

  }

  @Test
  void effectiveExplainReturnsExplainWhenSet() {
    var report = ExplainReport.builder().name("WithExplainTx").description("explain").mermaid("")
            .build();
    var tx = Dsl.transaction("WithExplainTx")
            .execute(ctx -> Result.success("exec"))
            .explain(ctx -> Result.success(report))
            .build();
    assertThat(tx.effectiveExplain()).isSameAs(tx.explainLogic());
    assertThat(tx.effectiveExplain()).isNotSameAs(tx.executeLogic());
  }

  @Test
  void explainViaLoadsResourceMarkdown() {
    var tx = Dsl.transaction("DocTx")
            .execute(ctx -> Result.success("exec"))
            .explainVia("builder-sample.md")
            .build();
    var contextFactory = new ContextFactory();
    var ctx = new TransactionRichContext<>(
            contextFactory.of("body", ExecutionMode.EXPLAIN, "run-doc"), contextFactory);

    var result = tx.effectiveExplain().apply(ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().name()).isEqualTo("DocTx");
    assertThat(result.value().mermaid()).contains("# Builder Sample");
  }

  @Test
  void explainViaTruncatesMarkdownToMetadataBudget() {
    var tx = Dsl.transaction("DocTxBudget")
            .execute(ctx -> Result.success("exec"))
            .explainVia("builder-sample.md")
            .build();
    var contextFactory = new ContextFactory();
    var ctx = new TransactionRichContext<>(
            contextFactory.of("body",
                    Map.of(Constants.EXPLAIN_BUDGET_CHARS_KEY, 5),
                    ExecutionMode.EXPLAIN, "run-doc-budget"),
            contextFactory);

    var result = tx.effectiveExplain().apply(ctx);

    assertThat(result.value().mermaid()).hasSizeLessThanOrEqualTo(5);
  }

  @Test
  void explainViaIsLazyWhenResourceMissing() {
    var tx = Dsl.transaction("DocTxMissing")
            .execute(ctx -> Result.success("exec"))
            .explainVia("missing.md")
            .build();
    var contextFactory = new ContextFactory();
    var ctx = new TransactionRichContext<>(
            contextFactory.of("body", ExecutionMode.EXPLAIN, "run-doc-missing"), contextFactory);

    var result = tx.effectiveExplain().apply(ctx);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("classpath: explain/missing.md");
  }

  @Test
  void describeUsesCustomDescriptorSupplierWhenProvided() {
    var objectDescriptor = TransactionDescriptor.builder()
            .name("PayTx")
            .description("custom-desc")
            .version("v9")
            .taskQueue("custom-queue")
            .inputType(String.class)
            .outputType(String.class)
            .helperRefs(List.of())
            .startToCloseTimeout(Duration.ofSeconds(1))
            .build();
    var custom = DslDescriptor.builder()
            .objectDescriptor(objectDescriptor)
            .hasSideEffects(false)
            .parameters(List.of())
            .taskQueue("custom-queue")
            .version("v9")
            .startToCloseTimeout(Duration.ofSeconds(1))
            .build();
    var tx = Dsl.transaction("PayTx")
            .execute(ctx -> Result.success(null))
            .describe(() -> custom)
            .build();
    assertThat(tx.describe()).isSameAs(custom);
  }

  @Test
  void describeBuildsDefaultDescriptorWhenSupplierAbsent() {
    var tx = Dsl.transaction("PayTx")
            .input(String.class)
            .execute(ctx -> Result.success(null))
            .compensation(ctx -> Result.success(null))
            .build();
    var desc = tx.describe();
    assertThat(desc.name()).isEqualTo("PayTx");
    assertThat(desc.type()).isEqualTo(DslType.TRANSACTION);
    assertThat(desc.hasSideEffects()).isTrue();
    assertThat(desc.inputType()).isEqualTo(String.class);
    assertThat(desc.taskQueue()).isEqualTo("PayTx-queue");
    assertThat(desc.version()).isEqualTo("v1");
  }

  @Test
  void describeReportsSideEffectsByDefault() {
    var tx = Dsl.transaction("PayTx")
            .execute(ctx -> Result.success(null))
            .build();
    assertThat(tx.describe().hasSideEffects()).isTrue();
  }

  @Test
  void describeExplainReturnsMarkdown() {
    var paymentDescriptor = TransactionDescriptor.builder()
            .name("PayTx")
            .description("Processes a payment.")
            .version("v1")
            .taskQueue("PayTx-queue")
            .inputType(String.class)
            .outputType(String.class)
            .helperRefs(List.of())
            .startToCloseTimeout(Duration.ofSeconds(30))
            .build();
    var custom = DslDescriptor.builder()
            .objectDescriptor(paymentDescriptor)
            .hasSideEffects(true)
            .parameters(List.of())
            .taskQueue("PayTx-queue")
            .version("v1")
            .startToCloseTimeout(Duration.ofSeconds(30))
            .build();
    var tx = Dsl.transaction("PayTx")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success(null))
            .describe(() -> custom)
            .build();

    var markdown = DescriptorMarkdown.render(tx.describe());

    assertThat(markdown)
            .contains("**Transaction** `PayTx`")
            .contains("Processes a payment.")
            .contains("- Input: `String`")
            .contains("- Output: `String`")
            .contains("- Side effects: yes")
            .doesNotContain("Compensation");
  }

  @Test
  void implementsObjectBuilderInterface() {
    assertThat(Dsl.transaction("AnyTx")).isInstanceOf(cbs.nova.dsl.model.ObjectBuilder.class);
  }

  @Test
  void defaultExplainUsesExplainResourceByMetadataName() {
    var gm = GlobalManager.globalManager();
    gm.registerExplainResource(stubProvider(
            "LookupTx", "by-name", "lookup-tx.md", "# Tx By Name"));
    try {
      var tx = Dsl.transaction("LookupTx")
              .execute(ctx -> Result.success("exec"))
              .build();
      var contextFactory = new ContextFactory();
      var ctx = new TransactionRichContext<>(
              contextFactory.of("body", ExecutionMode.EXPLAIN, "run-tx-lookup-name"),
              contextFactory);

      var result = tx.effectiveExplain().apply(ctx);

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.value().mermaid()).isEqualTo("# Tx By Name");
    } finally {
      gm.resetForTests();
    }
  }

  @Test
  void defaultExplainUsesExplainResourceByFilename() {
    var gm = GlobalManager.globalManager();
    gm.registerExplainResource(stubProvider(
            "BatchPayment", "pays", "batch-payment.md", "# Tx Filename"));
    try {
      var tx = Dsl.transaction("BatchPayment")
              .execute(ctx -> Result.success("exec"))
              .build();
      var contextFactory = new ContextFactory();
      var ctx = new TransactionRichContext<>(
              contextFactory.of("body", ExecutionMode.EXPLAIN, "run-tx-lookup-file"),
              contextFactory);

      var result = tx.effectiveExplain().apply(ctx);

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.value().mermaid()).isEqualTo("# Tx Filename");
    } finally {
      gm.resetForTests();
    }
  }

  @Test
  void defaultExplainPrefersMetadataNameOverFilename() {
    var gm = GlobalManager.globalManager();
    gm.registerExplainResource(stubProvider(
            "PreferTx", "by-name", "prefer-tx.md", "# Name Wins"));
    gm.registerExplainResource(stubProvider(
            "PreferTxFile", "by-file", "prefertx.md", "# File Loses"));
    try {
      var tx = Dsl.transaction("PreferTx")
              .execute(ctx -> Result.success("exec"))
              .build();
      var contextFactory = new ContextFactory();
      var ctx = new TransactionRichContext<>(
              contextFactory.of("body", ExecutionMode.EXPLAIN, "run-tx-lookup-prefer"),
              contextFactory);

      var result = tx.effectiveExplain().apply(ctx);

      assertThat(result.value().mermaid()).isEqualTo("# Name Wins");
    } finally {
      gm.resetForTests();
    }
  }

  @Test
  void defaultExplainFallsBackToEmptyMarkdownWhenResourceMissing() {
    var tx = Dsl.transaction("MissingTx")
            .execute(ctx -> Result.success("exec"))
            .build();
    var contextFactory = new ContextFactory();
    var ctx = new TransactionRichContext<>(
            contextFactory.of("body", ExecutionMode.EXPLAIN, "run-tx-fallback"),
            contextFactory);

    var result = tx.effectiveExplain().apply(ctx);

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
}
