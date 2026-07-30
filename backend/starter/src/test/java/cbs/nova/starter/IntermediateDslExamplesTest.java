package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DefinitionLoader;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.HelperInstanceResolver;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dslexamples.BatchModels.BatchIn;
import cbs.nova.dslexamples.BatchModels.BatchItem;
import cbs.nova.dslexamples.BatchModels.BatchOut;
import cbs.nova.dslexamples.InvoiceModels.InvoiceIn;
import cbs.nova.dslexamples.InvoiceModels.InvoiceLine;
import cbs.nova.dslexamples.InvoiceModels.InvoiceOut;
import cbs.nova.dslexamples.LongWorkModels.LongWorkIn;
import cbs.nova.dslexamples.LongWorkModels.LongWorkOut;
import cbs.nova.starter.logging.ThreadLocalDryRunLoggingContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class IntermediateDslExamplesTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final ExecutionTraceCollector traceCollector = new ExecutionTraceCollector();
  private final ExternalCallTracker tracker = new ExternalCallTracker();
  private final ThreadLocalDryRunLoggingContext dryRunLoggingContext = new ThreadLocalDryRunLoggingContext();
  private final DevDslRuntime runtime = new DevDslRuntime(tracker, traceCollector, contextFactory,
          dryRunLoggingContext, 32);
  @TempDir
  Path dslSourceDir;

  @BeforeEach
  void loadCompactDsls() throws Exception {
    GlobalManager.globalManager().resetForTests();
    DslConfig.dslConfig().helperInstanceResolver().replace(reflectiveHelperResolver());
    copyCompactDsl("BatchProcessingDsl.java");
    copyCompactDsl("InvoiceGenerationDsl.java");
    copyCompactDsl("LongWorkSimulationDsl.java");

    new DefinitionLoader().load(dslSourceDir, GlobalManager.globalManager());
    GlobalManager.globalManager().registerHelperResolvers();
  }

  @AfterEach
  void cleanup() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void batchProcessingPreviewSumsAndSummarizes() {
    var input = new BatchIn(List.of(
            new BatchItem("a", 10),
            new BatchItem("b", 20),
            new BatchItem("c", 12)));
    Context<BatchIn> ctx = contextFactory.of(input, ExecutionMode.PREVIEW);

    Result<?> result = GlobalManager.globalManager().runProcess("BatchProcessing", ctx);

    assertThat(result.isSuccess()).isTrue();
    BatchOut out = (BatchOut) result.value();
    assertThat(out.total()).isEqualTo(42);
    assertThat(out.summary()).isEqualTo("Processed: a=10, b=20, c=12");
  }

  @Test
  void invoiceGenerationPreviewComputesTotals() {
    var input = new InvoiceIn(List.of(
            new InvoiceLine("widget", 2.50, 4),
            new InvoiceLine("gadget", 10.00, 2)));
    Context<InvoiceIn> ctx = contextFactory.of(input, ExecutionMode.PREVIEW);

    Result<?> result = GlobalManager.globalManager().runProcess("InvoiceGeneration", ctx);

    assertThat(result.isSuccess()).isTrue();
    InvoiceOut out = (InvoiceOut) result.value();
    assertThat(out.subtotal()).isEqualTo(30.0);
    assertThat(out.tax()).isEqualTo(6.0);
    assertThat(out.total()).isEqualTo(36.0);
    assertThat(out.formatted())
            .isEqualTo("Invoice: subtotal=30.00 tax=6.00 total=36.00");
  }

  @Test
  void longWorkSimulationPreviewCompletesAllSteps() {
    var input = new LongWorkIn("task-42", 5);
    Context<LongWorkIn> ctx = contextFactory.of(input, ExecutionMode.PREVIEW);

    Result<?> result = GlobalManager.globalManager().runTransaction("LongWorkSimulation", ctx);

    assertThat(result.isSuccess()).isTrue();
    LongWorkOut out = (LongWorkOut) result.value();
    assertThat(out.taskId()).isEqualTo("task-42");
    assertThat(out.status()).isEqualTo("COMPLETED");
    assertThat(out.stepsCompleted()).isEqualTo(5);
  }

  @Test
  void devDslRuntimePreviewReturnsSuccessReport() {
    var input = new BatchIn(List.of(new BatchItem("only", 7)));
    Context<BatchIn> ctx = contextFactory.of(input, ExecutionMode.PREVIEW);

    Result<PreviewReport> reportResult = runtime.preview("BatchProcessing", ctx);

    assertThat(reportResult.isSuccess()).isTrue();
    PreviewReport report = reportResult.value();
    assertThat(report.name()).isEqualTo("BatchProcessing");
    assertThat(report.success()).isTrue();
    BatchOut out = (BatchOut) report.output();
    assertThat(out.total()).isEqualTo(7);
  }

  private void copyCompactDsl(String name) throws Exception {
    try (InputStream in = getClass().getResourceAsStream("/dsl-intermediate-examples/" + name)) {
      if (in == null) {
        throw new IllegalStateException("Missing test resource: " + name);
      }
      Files.copy(in, dslSourceDir.resolve(name));
    }
  }

  private static HelperInstanceResolver reflectiveHelperResolver() {
    return helperClass -> {
      try {
        if (helperClass == cbs.nova.starter.helpers.HttpCallHelper.class) {
          return new cbs.nova.starter.helpers.HttpCallHelper(HttpClient.newHttpClient());
        }
        // TODO: remove reflection, use typed info instead
        return (Executable<?, ?>) helperClass.getDeclaredConstructor().newInstance();
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Cannot instantiate helper " + helperClass, e);
      }
    };
  }
}
