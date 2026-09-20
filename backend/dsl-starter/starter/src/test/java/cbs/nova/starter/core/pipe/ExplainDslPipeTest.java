package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.helper.HelperInstanceResolver;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.utils.DefinitionLoader;
import cbs.nova.starter.config.properties.CbsNovaExplainProperties;
import cbs.nova.starter.config.properties.CbsNovaFakesProperties;
import cbs.nova.starter.config.properties.CbsNovaPreviewProperties;
import cbs.nova.starter.config.properties.DryRunProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import cbs.nova.starter.helper.CompensationTrackerHelper;
import cbs.nova.starter.helper.UnreliableApiHelper;
import cbs.nova.starter.logging.DryRunLogBufferRegistry;
import cbs.nova.starter.logging.ThreadLocalDryRunLoggingContext;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExplainDslPipeTest {

  private static int defaultMaxEventsPerRun() {
    return new DryRunProperties(null, null).log().maxEventsPerRun();
  }

  private final ThreadLocalDryRunLoggingContext dryRunLoggingContext = new ThreadLocalDryRunLoggingContext();
  private final DryRunLogBufferRegistry bufferRegistry = new DryRunLogBufferRegistry(
          Caffeine.newBuilder().build());
  private final CbsNovaPreviewProperties previewProperties = new CbsNovaPreviewProperties(null,
          null, null);

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
    DslConfig.dslConfig().helperInstanceResolver().replace(null);
  }

  @Test
  void explainReturnsLightweightReportWithMermaidAndChildren() {
    GlobalManager.globalManager().registerHelper("auditLog",
            (cbs.nova.dsl.Executable<Object, Object>) ctx -> Result.success("audited"));
    GlobalManager.globalManager().registerProcess(
            Dsl.process("ExplainProcess")
                    .input(Object.class)
                    .output(Object.class)
                    .execute(ctx -> Result.success("ok"))
                    .explain(ctx -> {
                      ctx.runHelper("auditLog");
                      return Result.success(ExplainReport.builder().name("ExplainProcess")
                              .description("Process: ExplainProcess").markdown("m").build());
                    })
                    .compensation((ctx, history) -> ctx.log("rolled back"))
                    .build());

    ExplainDslPipe explainPipe = newPipe(mock(ExternalCallRecorder.class));

    Result<ExplainReport> result = explainPipe.execute("ExplainProcess",
            SimpleContext.builder("payload").mode(ExecutionMode.EXPLAIN).runId("run-explain")
                    .build());

    assertThat(result.isSuccess()).isTrue();
    ExplainReport report = result.value();
    assertThat(report.name()).isEqualTo("ExplainProcess");
    assertThat(report.description()).isEqualTo("Process: ExplainProcess");
    assertThat(report.markdown()).isNotBlank();
    assertThat(report.children()).isEmpty();
  }

  @Test
  void missingEntityFails() {
    ExplainDslPipe explainPipe = newPipe(mock(ExternalCallRecorder.class));

    Result<ExplainReport> result = explainPipe.execute("MissingProcess",
            SimpleContext.builder("payload").mode(ExecutionMode.EXPLAIN).runId("run-fail").build());

    assertThat(result.isSuccess()).isFalse();
  }

  @Test
  void explainViaResourceDrivesRootAndChildDescriptions() {
    loadCompactDsls();
    ExplainDslPipe explainPipe = newPipe(mock(ExternalCallRecorder.class));
    var body = new cbs.nova.dslexamples.v1.UnreliableApiModels.UnreliableProcessIn(
            "explain-test",
            new cbs.nova.starter.helper.model.UnreliableApiIn("op-1", 0, false, "explain", null));

    Result<ExplainReport> result = explainPipe.execute("UnreliableApiSuccess",
            SimpleContext.builder(body).mode(ExecutionMode.EXPLAIN).runId("run-resource")
                    .build());

    assertThat(result.isSuccess()).isTrue();
    ExplainReport report = result.value();
    assertThat(report.description())
            .startsWith("A process that routes an API call through the resilient transaction.");
    assertThat(report.markdown()).contains("# UnreliableApiSuccess");
    assertThat(report.children())
            .anySatisfy(child -> {
              assertThat(child.description())
                      .startsWith("A transaction that expects temporary failures.");
              assertThat(child.markdown()).contains("# unreliableApiTxResilient");
            });
  }

  @Test
  void unreliableApiSuccessExplainsAllThreeNodesInChain() {
    loadCompactDsls();
    ExplainDslPipe explainPipe = newPipe(mock(ExternalCallRecorder.class));
    var body = new cbs.nova.dslexamples.v1.UnreliableApiModels.UnreliableProcessIn(
            "explain-chain",
            new cbs.nova.starter.helper.model.UnreliableApiIn("op-1", 0, false, "explain", null));

    Result<ExplainReport> result = explainPipe.execute("UnreliableApiSuccess",
            SimpleContext.builder(body).mode(ExecutionMode.EXPLAIN).runId("run-chain").build());

    assertThat(result.isSuccess()).isTrue();
    ExplainReport root = result.value();
    assertThat(root.name()).isEqualTo("UnreliableApiSuccess");
    assertThat(root.children()).extracting(ExplainReport::name)
            .containsExactly("unreliableApiTxResilient");
    ExplainReport tx = root.children().get(0);
    assertThat(tx.children()).extracting(ExplainReport::name).containsExactly("unreliableApi");
    ExplainReport helper = tx.children().get(0);
    assertThat(root.description()).isNotBlank();
    assertThat(tx.description()).isNotBlank();
    assertThat(helper.description()).isNotBlank().contains("unreliable");
  }

  @Test
  void explainCollectsHelperExplainAsChild() {
    GlobalManager.globalManager().registerHelper("compensationTracker",
            new CompensationTrackerHelper(Caffeine.newBuilder().build()));
    GlobalManager.globalManager().registerProcess(
            Dsl.process("TrackedFlow")
                    .input(Object.class)
                    .output(Object.class)
                    .description("Tracked flow")
                    .execute(ctx -> ctx.runHelper("compensationTracker",
                            java.util.Map.of("markerId", "m-1")))
                    .build());

    Result<ExplainReport> result = newPipe(mock(ExternalCallRecorder.class)).execute(
            "TrackedFlow",
            SimpleContext.builder("payload").mode(ExecutionMode.EXPLAIN).runId("run-helper")
                    .build());

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().children()).singleElement().satisfies(child -> {
      assertThat(child.name()).isEqualTo("compensationTracker");
      assertThat(child.description()).contains("Write here some text");
      assertThat(child.markdown()).contains("mermaid diagram");
    });
  }

  @Test
  void missingExplainViaResourceFails() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("MissingExplainResource")
                    .input(Object.class)
                    .output(Object.class)
                    .explainVia("missing-resource.md")
                    .execute(ctx -> Result.success("ok"))
                    .build());
    ExplainDslPipe explainPipe = newPipe(mock(ExternalCallRecorder.class));

    Result<ExplainReport> result = explainPipe.execute("MissingExplainResource",
            SimpleContext.builder("payload").mode(ExecutionMode.EXPLAIN).runId("run-fallback")
                    .build());

    assertThat(result.isSuccess()).isFalse();
  }

  private void loadCompactDsls() {
    DslConfig.dslConfig().helperInstanceResolver().replace(typedHelperResolver());
    new DefinitionLoader().load(GlobalManager.globalManager());
    GlobalManager.globalManager().registerHelperResolvers();
  }

  private static HelperInstanceResolver typedHelperResolver() {
    return helperClass -> {
      if (helperClass == UnreliableApiHelper.class) {
        return new UnreliableApiHelper(Caffeine.newBuilder()
                .expireAfterWrite(StarterConstants.UNRELIABLE_API_TTL)
                .maximumSize(StarterConstants.UNRELIABLE_API_MAX_SIZE)
                .build());
      }
      if (helperClass == CompensationTrackerHelper.class) {
        return new CompensationTrackerHelper(Caffeine.newBuilder()
                .expireAfterWrite(StarterConstants.COMPENSATION_TRACKER_TTL)
                .maximumSize(StarterConstants.COMPENSATION_TRACKER_MAX_SIZE)
                .build());
      }
      try {
        var constructor = helperClass.getDeclaredConstructor();
        if (!constructor.canAccess(null)) {
          constructor.setAccessible(true);
        }
        return (cbs.nova.dsl.Executable<?, ?>) constructor.newInstance();
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Cannot instantiate helper " + helperClass.getName(), e);
      }
    };
  }

  private ExplainDslPipe newPipe(ExternalCallRecorder recorder) {
    return new ExplainDslPipe(new CbsNovaExplainProperties(4000, "explain/", 128, 256, 4096));
  }
}
