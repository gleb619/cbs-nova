package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.ServiceLoaderDslDefinitionLoader;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.helper.HelperInstanceResolver;
import cbs.nova.dslexamples.batchprocessing.v1.BatchModels.BatchIn;
import cbs.nova.dslexamples.batchprocessing.v1.BatchModels.BatchItem;
import cbs.nova.dslexamples.batchprocessing.v1.BatchModels.BatchOut;
import cbs.nova.dslexamples.invoicegeneration.v1.InvoiceModels.InvoiceIn;
import cbs.nova.dslexamples.invoicegeneration.v1.InvoiceModels.InvoiceLine;
import cbs.nova.dslexamples.invoicegeneration.v1.InvoiceModels.InvoiceOut;
import cbs.nova.dslexamples.longworksimulation.v1.LongWorkModels.LongWorkIn;
import cbs.nova.dslexamples.longworksimulation.v1.LongWorkModels.LongWorkOut;
import cbs.nova.starter.config.CbsNovaFakesProperties;
import cbs.nova.starter.config.CbsNovaPreviewProperties;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties.Level;
import cbs.nova.starter.core.pipe.ExplainDslPipe;
import cbs.nova.starter.core.pipe.PreviewDslPipe;
import cbs.nova.starter.core.pipe.RunDslPipe;
import cbs.nova.starter.core.pipe.RunScopedFakeConfig;
import cbs.nova.starter.core.recorder.RunIdKeyedExternalCallRecorder;
import cbs.nova.starter.logging.ThreadLocalDryRunLoggingContext;
import cbs.nova.starter.helper.*;
import cbs.nova.starter.logging.DryRunLogBufferRegistry;
import cbs.nova.starter.logging.DryRunLogbackAppender;
import cbs.nova.starter.logging.ThreadLocalDryRunLoggingContext;
import cbs.nova.starter.reporting.ExplainDiagramRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.util.List;

class IntermediateDslExamplesTest {
  private final ThreadLocalDryRunLoggingContext dryRunLoggingContext = new ThreadLocalDryRunLoggingContext();

  private final ContextFactory contextFactory = new ContextFactory();
  private final RunIdKeyedExternalCallRecorder recorder = new RunIdKeyedExternalCallRecorder(
          dryRunLoggingContext, null);
  private final DryRunLogBufferRegistry bufferRegistry = new DryRunLogBufferRegistry();
  private final CbsNovaPreviewProperties previewProperties = new CbsNovaPreviewProperties(null,
          null, null);
  private final PreviewDslPipe previewPipe = new PreviewDslPipe(recorder, contextFactory,
          dryRunLoggingContext, bufferRegistry, DryRunLogbackAppender.DEFAULT_MAX_EVENTS_PER_RUN,
          null, previewProperties, new CbsNovaFakesProperties(false, null),
          new RunScopedFakeConfig(), new SimpleMeterRegistry(), null);
  private final RunDslPipe runPipe = new RunDslPipe(contextFactory, recorder,
          new CbsNovaFakesProperties(false, null), new RunScopedFakeConfig());
  private final ExplainDslPipe explainPipe = new ExplainDslPipe(recorder, contextFactory,
          dryRunLoggingContext, bufferRegistry, DryRunLogbackAppender.DEFAULT_MAX_EVENTS_PER_RUN,
          previewProperties, new CbsNovaFakesProperties(false, null), new RunScopedFakeConfig(),
          new SimpleMeterRegistry(), new ExplainDiagramRenderer(), null);
  private final DevDslRuntime runtime = new DevDslRuntime(previewPipe, runPipe, explainPipe);

  @BeforeEach
  void loadCompactDsls() {
    GlobalManager.globalManager().resetForTests();
    DslConfig.dslConfig().helperInstanceResolver().replace(typedHelperResolver());
    new ServiceLoaderDslDefinitionLoader().load(GlobalManager.globalManager());
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

  private static HelperInstanceResolver typedHelperResolver() {
    return helperClass -> {
      if (helperClass == ConditionalFailingHelper.class) {
        return new ConditionalFailingHelper();
      }
      if (helperClass == CompensationTrackerHelper.class) {
        return new CompensationTrackerHelper();
      }
      if (helperClass == CurrentTimestampHelper.class) {
        return new CurrentTimestampHelper();
      }
      if (helperClass == FileLatchHelper.class) {
        return new FileLatchHelper();
      }
      if (helperClass == FilterRecordsHelper.class) {
        return new FilterRecordsHelper();
      }
      if (helperClass == FormatMessageHelper.class) {
        return new FormatMessageHelper();
      }
      if (helperClass == HttpCallHelper.class) {
        return new HttpCallHelper(HttpClient.newHttpClient(),
                new CbsNovaLoggingProperties(Level.INFO, Level.INFO, true));
      }
      if (helperClass == JsonExtractHelper.class) {
        return new JsonExtractHelper(new ObjectMapper());
      }
      if (helperClass == SortRecordsHelper.class) {
        return new SortRecordsHelper();
      }
      if (helperClass == ArithmeticHelper.class) {
        return new ArithmeticHelper();
      }
      if (helperClass == UnreliableApiHelper.class) {
        return new UnreliableApiHelper();
      }
      if (helperClass == UuidV7Helper.class) {
        return new UuidV7Helper();
      }
      if (helperClass == FormatDateHelper.class) {
        return new FormatDateHelper();
      }
      if (helperClass == ParseDateHelper.class) {
        return new ParseDateHelper();
      }
      if (helperClass == Base64Helper.class) {
        return new Base64Helper();
      }

      throw new IllegalStateException("Cannot instantiate helper " + helperClass.getName());
    };
  }
}
