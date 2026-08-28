package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cbs.nova.dsl.DslErrorCode;
import cbs.nova.dsl.DslRuntime;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.PreviewErrorCode;
import cbs.nova.dsl.PreviewErrorDetail;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.exception.DslException;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties;
import cbs.nova.starter.converter.DslRuntimeMapper;
import cbs.nova.starter.logging.LoggingExecutionListener;
import cbs.nova.starter.model.DslRequest;
import cbs.nova.starter.model.RuntimeOutcome;
import cbs.nova.starter.web.RequestIdFilter;
import java.util.List;
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
            new ContextFactory(),
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
    assertThat(MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY)).isNull();
  }

  @Test
  void previewReturnsErrorForFailedReport() {
    PreviewReport report = previewReport("Ping", false,
            List.of(new PreviewErrorDetail(PreviewErrorCode.UNKNOWN_ERROR, "boom", "fix",
                    Map.of())));
    doReturn(Result.success(report)).when(dslRuntime).preview(eq("Ping"), any());

    RuntimeOutcome outcome = service.preview("Ping", new DslRequest("hello", null), "req-1");

    assertThat(outcome.success()).isFalse();
    assertThat(outcome.value()).isNull();
    assertThat(outcome.error().code()).isEqualTo("UNKNOWN_ERROR");
    assertThat(outcome.error().message()).isEqualTo("boom");
    assertThat(outcome.error().entityName()).isEqualTo("Ping");
    assertThat(outcome.error().runId()).isEqualTo("req-1");
    assertThat(outcome.error().exceptionId()).startsWith("req-1:ex:");
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
    assertThat(outcome.error().code()).isEqualTo("EXECUTION_FAILED");
    assertThat(outcome.error().message()).isEqualTo("exec error");
    assertThat(outcome.error().runId()).isEqualTo("req-2");
  }

  @Test
  void runReturnsErrorWithDslExceptionFields() {
    var ex = new DslException("run-abc", DslErrorCode.ENTITY_NOT_FOUND, "not found");
    doReturn(Result.failure(ex)).when(dslRuntime).run(eq("P"), any());

    RuntimeOutcome outcome = service.run("P", new DslRequest("input", null), "req-3");

    assertThat(outcome.success()).isFalse();
    assertThat(outcome.error().code()).isEqualTo("ENTITY_NOT_FOUND");
    assertThat(outcome.error().runId()).isEqualTo("run-abc");
    assertThat(outcome.error().exceptionId()).startsWith("run-abc:ex:");
  }

  @Test
  void runFallsBackToGeneratedRunIdWhenHeaderMissing() {
    doReturn(Result.success("output")).when(dslRuntime).run(eq("P"), any());

    service.run("P", new DslRequest("input", null), null);

    ArgumentCaptor<cbs.nova.dsl.Context<?>> captor = ArgumentCaptor
            .forClass(cbs.nova.dsl.Context.class);
    verify(dslRuntime).run(eq("P"), captor.capture());
    assertThat(captor.getValue().runId()).startsWith("run-");
  }

  @Test
  void explainDelegatesToRuntime() {
    ExplainReport report = new ExplainReport(
            "P", "desc", List.of(), List.of(), Map.of(), null, null, null, List.of(), null,
            List.of(), null);
    doReturn(report).when(dslRuntime).explain(eq("P"), any());

    ExplainReport result = service.explain("P", new DslRequest("in", null), "req-4");

    assertThat(result).isSameAs(report);
    assertThat(MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY)).isNull();
  }

  @Test
  void mdcIsClearedAfterExecution() {
    doReturn(Result.success("output")).when(dslRuntime).run(eq("P"), any());

    assertThat(MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY)).isNull();
    service.run("P", new DslRequest("in", null), "rid-9");
    assertThat(MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY)).isNull();
  }

  private static PreviewReport previewReport(String name, boolean success,
          List<PreviewErrorDetail> errors) {
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
}
