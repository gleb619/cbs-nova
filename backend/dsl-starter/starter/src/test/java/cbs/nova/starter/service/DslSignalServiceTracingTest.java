package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowStub;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.ArgumentMatchers.eq;

/**
 * T746: asserts that {@link DslSignalService#sendSignal} and
 * {@link DslSignalService#querySignalState} emit their respective spans with the {@code runId},
 * {@code signalName} (send only), and {@code outcome} attributes, and that exceptions mark the span
 * as ERROR.
 */
class DslSignalServiceTracingTest {

  private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

  private InMemorySpanExporter exporter;
  private SdkTracerProvider tracerProvider;
  private WorkflowClient workflowClient;
  private InMemoryDslRunRepository repository;
  private DslSignalService service;

  @BeforeEach
  void setUp() {
    exporter = InMemorySpanExporter.create();
    tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build();
    OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();
    workflowClient = Mockito.mock(WorkflowClient.class);
    repository = new InMemoryDslRunRepository(InMemoryDslRunRepository.NO_OP_EVICTION);
    @SuppressWarnings("unchecked")
    ObjectProvider<OpenTelemetry> provider = Mockito.mock(ObjectProvider.class);
    Mockito.when(provider.getIfAvailable()).thenReturn(openTelemetry);
    service = new DslSignalService(workflowClient, repository, provider);
  }

  @AfterEach
  void tearDown() {
    tracerProvider.close();
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void sendSignalEmitsSpanWithRunIdSignalNameAndOutcome() {
    WorkflowStub stub = Mockito.mock(WorkflowStub.class);
    Mockito.when(workflowClient.newUntypedWorkflowStub("run-1")).thenReturn(stub);
    repository.save(runningRun("run-1"));

    DslSignalService.SignalResult result = service.sendSignal("run-1", "approve",
            Map.of("approved", true));

    assertThat(result.outcome()).isEqualTo(DslSignalService.Outcome.SENT);

    SpanData span = findSpan("dsl.signal.send");
    assertThat(span).isNotNull();
    assertThat(span.getAttributes().get(AttributeKey.stringKey("runId"))).isEqualTo("run-1");
    assertThat(span.getAttributes().get(AttributeKey.stringKey("signalName")))
            .isEqualTo("approve");
    assertThat(span.getAttributes().get(AttributeKey.stringKey("outcome"))).isEqualTo("sent");
    assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
  }

  @Test
  void sendSignalRecordsErrorOnStubFailure() {
    WorkflowStub stub = Mockito.mock(WorkflowStub.class);
    Mockito.when(workflowClient.newUntypedWorkflowStub("run-1")).thenReturn(stub);
    Mockito.doThrow(new RuntimeException("stub boom")).when(stub).signal(eq("approve"));
    repository.save(runningRun("run-1"));

    try {
      service.sendSignal("run-1", "approve", null);
    } catch (RuntimeException expected) {
      // expected
    }

    SpanData span = findSpan("dsl.signal.send");
    assertThat(span).isNotNull();
    assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    assertThat(span.getAttributes().get(AttributeKey.stringKey("outcome"))).isEqualTo("error");
  }

  @Test
  void sendSignalEmitsNotFoundOutcomeWhenRunAbsent() {
    DslSignalService.SignalResult result = service.sendSignal("missing", "approve", null);

    assertThat(result.outcome()).isEqualTo(DslSignalService.Outcome.NOT_FOUND);

    SpanData span = findSpan("dsl.signal.send");
    assertThat(span).isNotNull();
    assertThat(span.getAttributes().get(AttributeKey.stringKey("outcome")))
            .isEqualTo("not_found");
  }

  @Test
  void querySignalStateEmitsSpanWithRunIdAndOkOutcome() {
    WorkflowStub stub = Mockito.mock(WorkflowStub.class);
    Mockito.when(workflowClient.newUntypedWorkflowStub("run-1")).thenReturn(stub);
    Mockito.when(stub.query(eq("dslSignalState"), eq(Map.class)))
            .thenReturn(Map.of("lastSignal", "approve"));
    repository.save(runningRun("run-1"));

    Map<String, Object> state = service.querySignalState("run-1");

    assertThat(state).containsEntry("lastSignal", "approve");

    SpanData span = findSpan("dsl.signal.query");
    assertThat(span).isNotNull();
    assertThat(span.getAttributes().get(AttributeKey.stringKey("runId"))).isEqualTo("run-1");
    assertThat(span.getAttributes().get(AttributeKey.stringKey("outcome"))).isEqualTo("ok");
  }

  @Test
  void querySignalStateEmitsNotFoundOutcomeWhenRunAbsent() {
    Map<String, Object> state = service.querySignalState("missing");

    assertThat(state).isNull();

    SpanData span = findSpan("dsl.signal.query");
    assertThat(span).isNotNull();
    assertThat(span.getAttributes().get(AttributeKey.stringKey("outcome")))
            .isEqualTo("not_found");
  }

  private SpanData findSpan(String name) {
    return exporter.getFinishedSpanItems().stream()
            .filter(s -> name.equals(s.getName()))
            .findFirst()
            .orElse(null);
  }

  private static DslRun runningRun(String runId) {
    return DslRun.builder()
            .runId(runId)
            .processName("LoanDisbursement")
            .status(DslRunStatus.RUNNING.name())
            .startedAt(NOW.minusSeconds(30))
            .executionMode("RUN")
            .build();
  }

}
