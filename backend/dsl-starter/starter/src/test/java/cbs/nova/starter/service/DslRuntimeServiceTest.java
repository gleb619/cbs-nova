package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslErrorCode;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.DslRuntime;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.model.Descriptors;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.PreviewErrorCode;
import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.dsl.model.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.exception.DslException;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dslexamples.v1.BatchModels.BatchIn;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.core.pipe.PreviewTimeoutException;
import cbs.nova.starter.converter.DslRuntimeMapper;
import cbs.nova.starter.logging.LoggingExecutionListener;
import cbs.nova.starter.model.DslRequest;
import cbs.nova.starter.model.RuntimeOutcome;
import java.util.LinkedHashMap;
import java.util.List;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

class DslRuntimeServiceTest {

  private DslRuntime dslRuntime;
  private DslRuntimeService service;

  @BeforeEach
  void setUp() {
    dslRuntime = mock(DslRuntime.class);
    DslRuntimeMapper mapper = Mappers.getMapper(DslRuntimeMapper.class);
    var loggingProps = new CbsNovaLoggingProperties(
            CbsNovaLoggingProperties.Level.INFO,
            CbsNovaLoggingProperties.Level.INFO,
            false);
    service = new DslRuntimeService(
            dslRuntime,

            new LoggingExecutionListener(loggingProps),
            mapper);
  }

  @Test
  void previewReturnsOkForSuccessfulReport() {
    PreviewReport report = previewReport("Ping", true, List.of());
    doReturn(Result.success(report)).when(dslRuntime).preview(eq("Ping"), any());

    RuntimeOutcome outcome = service.preview("Ping", new DslRequest("hello", null), "req-1");

    assertThat(outcome.success()).isTrue();
    assertThat(outcome.value()).isSameAs(report);
    assertThat(outcome.error()).isNull();
    assertThat(MDC.get(StarterConstants.REQUEST_ID_MDC_KEY)).isNull();
  }

  @Test
  void previewReturnsErrorForFailedReport() {
    PreviewReport report = previewReport("Ping", false,
            List.of(new ErrorResponse("UNKNOWN_ERROR", "boom", null, null, null, null, null, "fix",
                    Map.of())));
    doReturn(Result.success(report)).when(dslRuntime).preview(eq("Ping"), any());

    RuntimeOutcome outcome = service.preview("Ping", new DslRequest("hello", null), "req-1");

    assertThat(outcome.success()).isFalse();
    assertThat(outcome.value()).isNull();
    assertThat(outcome.error().getCode()).isEqualTo("UNKNOWN_ERROR");
    assertThat(outcome.error().getMessage()).isEqualTo("boom");
    assertThat(outcome.error().getEntityName()).isEqualTo("Ping");
    assertThat(outcome.error().getRunId()).isEqualTo("req-1");
    assertThat(outcome.error().getExceptionId()).startsWith("req-1:ex:");
  }

  @Test
  void runReturnsOkForSuccessfulExecution() {
    doReturn(Result.success("output")).when(dslRuntime).run(eq("P"), any());

    RuntimeOutcome outcome = service.run("P", new DslRequest("input", null), null);

    assertThat(outcome.success()).isTrue();
    assertThat(outcome.value()).isEqualTo("output");
    assertThat(outcome.error()).isNull();
  }

  @Test
  void runReturnsErrorForGenericThrowable() {
    doReturn(Result.failure(new RuntimeException("exec error")))
            .when(dslRuntime).run(eq("P"), any());

    RuntimeOutcome outcome = service.run("P", new DslRequest("input", null), "req-2");

    assertThat(outcome.success()).isFalse();
    assertThat(outcome.error().getCode()).isEqualTo("EXECUTION_FAILED");
    assertThat(outcome.error().getMessage()).isEqualTo("exec error");
    assertThat(outcome.error().getRunId()).isEqualTo("req-2");
  }

  @Test
  void runReturnsErrorWithDslExceptionFields() {
    var ex = new DslException("run-abc", DslErrorCode.ENTITY_NOT_FOUND, "not found");
    doReturn(Result.failure(ex)).when(dslRuntime).run(eq("P"), any());

    RuntimeOutcome outcome = service.run("P", new DslRequest("input", null), "req-3");

    assertThat(outcome.success()).isFalse();
    assertThat(outcome.error().getCode()).isEqualTo("ENTITY_NOT_FOUND");
    assertThat(outcome.error().getRunId()).isEqualTo("run-abc");
    assertThat(outcome.error().getExceptionId()).startsWith("run-abc:ex:");
  }

  @Test
  void runFallsBackToGeneratedRunIdWhenHeaderMissing() {
    doReturn(Result.success("output")).when(dslRuntime).run(eq("P"), any());

    service.run("P", new DslRequest("input", null), null);

    ArgumentCaptor<Context<?>> captor = ArgumentCaptor
            .forClass(Context.class);
    verify(dslRuntime).run(eq("P"), captor.capture());
    assertThat(captor.getValue().runId()).startsWith("run-");
  }

  @Test
  void explainReturnsStructuredErrorWhenRuntimeThrows() {
    doThrow(new RuntimeException("boom")).when(dslRuntime).explain(eq("Ghost"), any());

    RuntimeOutcome result = service.explain("Ghost", new DslRequest("in", null), "req-boom");

    assertThat(result.success()).isFalse();
    assertThat(result.value()).isNull();
    assertThat(result.error()).isNotNull();
    assertThat(result.error().getCode()).isNotBlank();
    assertThat(result.error().getMessage()).isEqualTo("boom");
    assertThat(result.error().getRunId()).isEqualTo("req-boom");
    assertThat(result.error().getEntityName()).isEqualTo("Ghost");
  }

  @Test
  void explainReturnsStructuredErrorWhenReportNull() {
    doReturn(null).when(dslRuntime).explain(eq("Ghost"), any());

    RuntimeOutcome result = service.explain("Ghost", new DslRequest("in", null), "req-null");

    assertThat(result.success()).isFalse();
    assertThat(result.value()).isNull();
    assertThat(result.error()).isNotNull();
    assertThat(result.error().getRunId()).isEqualTo("req-null");
    assertThat(result.error().getEntityName()).isEqualTo("Ghost");
    assertThat(result.error().getMessage()).contains("Ghost");
    assertThat(result.error().getCode()).isNotBlank();
  }

  @Test
  void explainDelegatesToRuntime() {
    ExplainReport report = new ExplainReport("P", "desc", "graph TD\n  P[P]", List.of());
    doReturn(report).when(dslRuntime).explain(eq("P"), any());

    RuntimeOutcome result = service.explain("P", new DslRequest("in", null), "req-4");

    assertThat(result.success()).isTrue();
    assertThat(result.value()).isSameAs(report);
    assertThat(result.error()).isNull();
    assertThat(MDC.get(StarterConstants.REQUEST_ID_MDC_KEY)).isNull();
  }

  @Test
  void explainReturnsTimeoutErrorWhenRuntimeThrowsPreviewTimeout() {
    doThrow(new PreviewTimeoutException("Slow", Duration.ofMillis(100)))
            .when(dslRuntime).explain(eq("Slow"), any());

    RuntimeOutcome result = service.explain("Slow", new DslRequest("in", null), "req-5");

    assertThat(result.success()).isFalse();
    assertThat(result.error().getCode()).isEqualTo("PREVIEW_TIMEOUT");
    assertThat(result.error().getEntityName()).isEqualTo("Slow");
    assertThat(result.error().getRunId()).isEqualTo("req-5");
    assertThat(result.error().getExceptionId()).startsWith("req-5:ex:");
  }

  @Test
  void previewReturnsTimeoutErrorWhenResultFailsWithPreviewTimeoutException() {
    PreviewTimeoutException timeout = new PreviewTimeoutException("Slow", Duration.ofMillis(100));
    doReturn(Result.failure(timeout)).when(dslRuntime).preview(eq("Slow"), any());

    RuntimeOutcome result = service.preview("Slow", new DslRequest("in", null), "req-6");

    assertThat(result.success()).isFalse();
    assertThat(result.error().getCode()).isEqualTo("PREVIEW_TIMEOUT");
    assertThat(result.error().getEntityName()).isEqualTo("Slow");
    assertThat(result.error().getRunId()).isEqualTo("req-6");
    assertThat(result.error().getMessage()).contains("100 ms");
  }

  @Test
  void mdcIsClearedAfterExecution() {
    doReturn(Result.success("output")).when(dslRuntime).run(eq("P"), any());

    assertThat(MDC.get(StarterConstants.REQUEST_ID_MDC_KEY)).isNull();
    service.run("P", new DslRequest("in", null), "rid-9");
    assertThat(MDC.get(StarterConstants.REQUEST_ID_MDC_KEY)).isNull();
  }

  private static PreviewReport previewReport(String name, boolean success,
          List<ErrorResponse> errors) {
    return new PreviewReport(
            name,
            ExecutionMode.PREVIEW,
            success,
            success ? "pong" : null,
            List.of(),
            List.of(),
            Map.of(),
            null,
            List.of(),
            null,
            errors);
  }

  @Test
  void runUsesForcedRunIdWhenProvided() {
    doReturn(Result.success("output")).when(dslRuntime).run(eq("P"), any());

    RuntimeOutcome outcome = service.run("P", new DslRequest("input", null), "req-7", "idem-abc");

    assertThat(outcome.success()).isTrue();
    assertThat(outcome.replayed()).isFalse();
    ArgumentCaptor<Context<?>> captor = ArgumentCaptor
            .forClass(Context.class);
    verify(dslRuntime).run(eq("P"), captor.capture());
    assertThat(captor.getValue().runId()).isEqualTo("idem-abc");
  }

  @Test
  void runStoresCorrelationIdInContextMetadata() {
    doReturn(Result.success("output")).when(dslRuntime).run(eq("P"), any());

    service.run("P", new DslRequest("input", null), "req-corr", null, "corr-123");

    ArgumentCaptor<Context<?>> captor = ArgumentCaptor
            .forClass(Context.class);
    verify(dslRuntime).run(eq("P"), captor.capture());
    assertThat(captor.getValue().metadata())
            .containsEntry(StarterConstants.CORRELATION_ID_METADATA_KEY, "corr-123");
  }

  @Test
  void runOmitsNullCorrelationIdFromMetadata() {
    doReturn(Result.success("output")).when(dslRuntime).run(eq("P"), any());

    service.run("P", new DslRequest("input", null), "req-corr", null, null);

    ArgumentCaptor<Context<?>> captor = ArgumentCaptor
            .forClass(Context.class);
    verify(dslRuntime).run(eq("P"), captor.capture());
    assertThat(captor.getValue().metadata())
            .doesNotContainKey(StarterConstants.CORRELATION_ID_METADATA_KEY);
  }

  @Test
  void runReturnsReplayedOutcomeWhenLauncherReportsAlreadyStarted() {
    doReturn(Result.failure(new IdempotentReplayException("idem-abc")))
            .when(dslRuntime).run(eq("P"), any());

    RuntimeOutcome outcome = service.run("P", new DslRequest("input", null), "req-8", "idem-abc");

    assertThat(outcome.success()).isTrue();
    assertThat(outcome.replayed()).isTrue();
    assertThat(outcome.value()).isEqualTo(Map.of("runId", "idem-abc", "status", "REPLAYED"));
    assertThat(outcome.error()).isNull();
  }

  @Test
  void runWithoutForcedRunIdKeepsExistingPath() {
    doReturn(Result.success("output")).when(dslRuntime).run(eq("P"), any());

    RuntimeOutcome outcome = service.run("P", new DslRequest("input", null), "req-9");

    assertThat(outcome.success()).isTrue();
    assertThat(outcome.replayed()).isFalse();
    ArgumentCaptor<Context<?>> captor = ArgumentCaptor
            .forClass(Context.class);
    verify(dslRuntime).run(eq("P"), captor.capture());
    assertThat(captor.getValue().runId()).isEqualTo("req-9");
  }

  @Test
  void previewCoercesMapBodyIntoTypedInputRecord() {
    // Register a synthetic process directly so GlobalManager lookup resolves its inputType
    // without dragging in helper-instance resolution.
    GlobalManager.globalManager().resetForTests();
    try {
      var process = ProcessDslObject.builder()
              .name("Synthetic")
              .description(Constants.EMPTY_MARKDOWN)
              .taskQueue("default")
              .version("v1")
              .inputType(BatchIn.class)
              .outputType(Void.class)
              .parameters(List.of())
              .executeLogic(ctx -> Result.success("ok"))
              .previewLogic(ctx -> Result.success("ok"))
              .explainLogic(
                      ctx -> Result.success(new ExplainReport("Synthetic", "test", "", List.of())))
              .descriptor(Descriptors.from("Synthetic",
                      new ExecutableDescriptor(
                              "Synthetic", null, null, null, false, null, List.of())))
              .build();
      GlobalManager.globalManager().registerProcess(process);

      PreviewReport report = previewReport("Synthetic", true, List.of());
      doReturn(Result.success(report)).when(dslRuntime).preview(eq("Synthetic"), any());

      Map<String, Object> rawMap = new LinkedHashMap<>();
      rawMap.put("items", List.of(
              Map.of("id", "a", "value", 10),
              Map.of("id", "b", "value", 20)));

      service.preview("Synthetic", new DslRequest(rawMap, null), "req-coerce");

      ArgumentCaptor<Context<?>> captor = ArgumentCaptor.forClass(Context.class);
      verify(dslRuntime).preview(eq("Synthetic"), captor.capture());
      Object coerced = captor.getValue().body();
      assertThat(coerced).isInstanceOf(BatchIn.class);
      BatchIn in = (BatchIn) coerced;
      assertThat(in.items()).hasSize(2);
      assertThat(in.items().get(0).id()).isEqualTo("a");
      assertThat(in.items().get(0).value()).isEqualTo(10);
    } finally {
      GlobalManager.globalManager().resetForTests();
    }
  }

  @Test
  void previewLeavesNonMapBodyUntouched() {
    GlobalManager.globalManager().resetForTests();
    try {
      var process = ProcessDslObject.builder()
              .name("Synthetic")
              .description(Constants.EMPTY_MARKDOWN)
              .taskQueue("default")
              .version("v1")
              .inputType(BatchIn.class)
              .outputType(Void.class)
              .parameters(List.of())
              .executeLogic(ctx -> Result.success("ok"))
              .previewLogic(ctx -> Result.success("ok"))
              .explainLogic(
                      ctx -> Result.success(new ExplainReport("Synthetic", "test", "", List.of())))
              .descriptor(Descriptors.from("Synthetic",
                      new ExecutableDescriptor(
                              "Synthetic", null, null, null, false, null, List.of())))
              .build();
      GlobalManager.globalManager().registerProcess(process);

      PreviewReport report = previewReport("Synthetic", true, List.of());
      doReturn(Result.success(report)).when(dslRuntime).preview(eq("Synthetic"), any());

      Object stringBody = "not-a-map";
      service.preview("Synthetic", new DslRequest(stringBody, null), "req-str");

      ArgumentCaptor<Context<?>> captor = ArgumentCaptor.forClass(Context.class);
      verify(dslRuntime).preview(eq("Synthetic"), captor.capture());
      assertThat(captor.getValue().body()).isSameAs(stringBody);
    } finally {
      GlobalManager.globalManager().resetForTests();
    }
  }
}
