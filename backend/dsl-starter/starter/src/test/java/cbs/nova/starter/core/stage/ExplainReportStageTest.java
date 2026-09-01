package cbs.nova.starter.core.stage;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.CallNode;
import cbs.nova.dsl.Context;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.PreviewErrorCode;
import cbs.nova.dsl.PreviewErrorDetail;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.core.recorder.ExternalCall;
import cbs.nova.starter.reporting.ExplainDiagramRenderer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExplainReportStageTest {

  private final ContextFactory contextFactory = new ContextFactory();

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void alwaysReturnsSuccessRegardlessOfInnerDslResultOutcome() {
    DslPipeContext pipeContext = pipeContext("unregistered-" + System.nanoTime(),
            ExecutionMode.PREVIEW);
    DslPipeStage.Next next = c -> {
      pipeContext.setAttribute("dslResult", Result.failure(new RuntimeException("boom")));
      return Result.failure(new RuntimeException("downstream"));
    };

    Result<?> result = new ExplainReportStage(new ExplainDiagramRenderer()).execute(pipeContext,
            next);

    assertThat(result.isSuccess()).isTrue();
    ExplainReport report = (ExplainReport) result.value();
    assertThat(report.errors()).hasSize(1);
    assertThat(report.errors().get(0).message()).contains("boom");
  }

  @Test
  void descriptionIsBuiltFromDescriptorTypeWhenFound() {
    String fnName = "MyFn-" + System.nanoTime();
    GlobalManager.globalManager().registerFunction(
            Dsl.function(fnName)
                    .execute(c -> Result.success("ok"))
                    .describe(() -> new DslDescriptor(
                            fnName,
                            DslObject.DslType.FUNCTION,
                            null,
                            null,
                            null,
                            false,
                            false,
                            "delegates",
                            List.of(),
                            null,
                            null,
                            null,
                            null))
                    .build());

    DslPipeContext pipeContext = pipeContext(fnName, ExecutionMode.PREVIEW);
    DslPipeStage.Next next = c -> Result.success("downstream");

    Result<?> result = new ExplainReportStage(new ExplainDiagramRenderer()).execute(pipeContext,
            next);

    ExplainReport report = (ExplainReport) result.value();
    assertThat(report.description()).isEqualTo("Function: " + fnName);
    assertThat(report.dslDescriptor()).isNotNull();
    assertThat(report.dslDescriptor().type()).isEqualTo(DslObject.DslType.FUNCTION);
  }

  @Test
  void descriptionFallsBackToHelperPrefixWhenHelperIsRegistered() {
    String helperName = "echo-helper-" + System.nanoTime();
    GlobalManager.globalManager().registerHelper(helperName, new EchoHelper());

    DslPipeContext pipeContext = pipeContext(helperName, ExecutionMode.PREVIEW);
    DslPipeStage.Next next = c -> Result.success("downstream");

    Result<?> result = new ExplainReportStage(new ExplainDiagramRenderer()).execute(pipeContext,
            next);

    ExplainReport report = (ExplainReport) result.value();
    assertThat(report.description()).isEqualTo("Helper: " + helperName);
    assertThat(report.dslDescriptor()).isNull();
    assertThat(report.executableDescriptor()).isNotNull();
  }

  @Test
  void descriptionFallsBackToEntityPrefixWhenNothingRegistered() {
    String orphan = "orphan-" + System.nanoTime();
    DslPipeContext pipeContext = pipeContext(orphan, ExecutionMode.PREVIEW);
    DslPipeStage.Next next = c -> Result.success("downstream");

    Result<?> result = new ExplainReportStage(new ExplainDiagramRenderer()).execute(pipeContext,
            next);

    ExplainReport report = (ExplainReport) result.value();
    assertThat(report.description()).isEqualTo("Entity: " + orphan);
    assertThat(report.dslDescriptor()).isNull();
    assertThat(report.executableDescriptor()).isNull();
  }

  @Test
  void errorsAreEmptyWhenDslResultSucceeded() {
    DslPipeContext pipeContext = pipeContext("unregistered-" + System.nanoTime(),
            ExecutionMode.PREVIEW);
    DslPipeStage.Next next = c -> {
      pipeContext.setAttribute("dslResult", Result.success("ok"));
      return Result.success("downstream");
    };

    Result<?> result = new ExplainReportStage(new ExplainDiagramRenderer()).execute(pipeContext,
            next);

    ExplainReport report = (ExplainReport) result.value();
    assertThat(report.errors()).isEmpty();
  }

  @Test
  void errorsAreEmptyWhenDslResultAttributeMissing() {
    DslPipeContext pipeContext = pipeContext("unregistered-" + System.nanoTime(),
            ExecutionMode.PREVIEW);
    DslPipeStage.Next next = c -> Result.success("downstream");

    Result<?> result = new ExplainReportStage(new ExplainDiagramRenderer()).execute(pipeContext,
            next);

    ExplainReport report = (ExplainReport) result.value();
    assertThat(report.errors()).isEmpty();
  }

  @Test
  void errorsPopulatedFromFailedDslResult() {
    DslPipeContext pipeContext = pipeContext("Ping", ExecutionMode.PREVIEW);
    DslPipeStage.Next next = c -> {
      pipeContext.setAttribute("dslResult", Result.failure(new RuntimeException("kaboom")));
      return Result.success("downstream");
    };

    Result<?> result = new ExplainReportStage(new ExplainDiagramRenderer()).execute(pipeContext,
            next);

    ExplainReport report = (ExplainReport) result.value();
    assertThat(report.errors()).hasSize(1);
    PreviewErrorDetail detail = report.errors().get(0);
    assertThat(detail.code()).isEqualTo(PreviewErrorCode.UNKNOWN_ERROR);
    assertThat(detail.message()).isEqualTo("kaboom");
  }

  @Test
  void externalCallsAndCallCountsDefaultToEmptyWhenAttributeMissing() {
    DslPipeContext pipeContext = pipeContext("unregistered-" + System.nanoTime(),
            ExecutionMode.PREVIEW);
    DslPipeStage.Next next = c -> Result.success("downstream");

    Result<?> result = new ExplainReportStage(new ExplainDiagramRenderer()).execute(pipeContext,
            next);

    ExplainReport report = (ExplainReport) result.value();
    assertThat(report.externalCalls()).isEmpty();
    assertThat(report.callCounts()).isEmpty();
  }

  @Test
  void externalCallsAndCallCountsArePopulatedFromAttribute() {
    DslPipeContext pipeContext = pipeContext("unregistered-" + System.nanoTime(),
            ExecutionMode.PREVIEW);
    List<ExternalCall> calls = List.of(
            new ExternalCall("database", "jdbc:db", "select", 0L, Map.of()),
            new ExternalCall("http", "http://x", "GET", 0L, Map.of()));
    pipeContext.setAttribute("externalCalls", calls);
    DslPipeStage.Next next = c -> Result.success("downstream");

    Result<?> result = new ExplainReportStage(new ExplainDiagramRenderer()).execute(pipeContext,
            next);

    ExplainReport report = (ExplainReport) result.value();
    assertThat(report.externalCalls()).hasSize(2);
    assertThat(report.callCounts())
            .containsEntry("database", 1)
            .containsEntry("http", 1);
  }

  @Test
  void attributeDefaultsAppliedWhenExecutionTraceAndDryRunLogsMissing() {
    DslPipeContext pipeContext = pipeContext("unregistered-" + System.nanoTime(),
            ExecutionMode.PREVIEW);
    DslPipeStage.Next next = c -> Result.success("downstream");

    Result<?> result = new ExplainReportStage(new ExplainDiagramRenderer()).execute(pipeContext,
            next);

    ExplainReport report = (ExplainReport) result.value();
    assertThat(report.executionTrace()).isEmpty();
    assertThat(report.dryRunLogs()).isEmpty();
    assertThat(report.astTree()).isNull();
    assertThat(report.metrics()).isNull();
  }

  private DslPipeContext pipeContext(String name, ExecutionMode mode) {
    Context<?> ctx = contextFactory.of("body", mode, "run-1");
    return new DslPipeContext(name, ctx, mode, "run-1");
  }

  private static final class EchoHelper implements Executable<Object, Object> {

    @Override
    public Result<Object> execute(Context<Object> ctx) {
      return Result.success("echo");
    }
  }
}
