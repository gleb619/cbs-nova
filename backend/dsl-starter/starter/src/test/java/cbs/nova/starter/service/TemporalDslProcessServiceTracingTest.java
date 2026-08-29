package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.SimpleContext;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class TemporalDslProcessServiceTracingTest {

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void runProcessCreatesAndEndsSpanWithAttributes() {
    InMemorySpanExporter exporter = InMemorySpanExporter.create();
    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build();
    OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();

    String runId = "run-trace-1";
    String processName = "missing-process";
    ContextFactory contextFactory = Mockito.mock(ContextFactory.class);
    Mockito.when(contextFactory.generateRunId()).thenReturn(runId);
    Mockito.when(contextFactory.of(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
            .thenReturn(new SimpleContext<>(
                    Map.of(), Map.of(), cbs.nova.dsl.ExecutionMode.RUN,
                    runId, cbs.nova.dsl.transaction.TransactionRouting.LOCAL, null, null, null));

    TemporalDslProcessService service = new TemporalDslProcessService(
            contextFactory,
            new InMemoryDslRunRepository(),
            new ObjectMapper(),
            synchronousExecutor(),
            disabledScheduledExecutor(),
            Duration.ofSeconds(30),
            Duration.ofMinutes(5),
            false);
    service.setOpenTelemetry(openTelemetry);

    service.startProcess(processName, Map.of(), Map.of()).result().join();

    assertThat(exporter.getFinishedSpanItems()).hasSize(1);
    SpanData span = exporter.getFinishedSpanItems().get(0);
    assertThat(span.getName()).isEqualTo("dsl.run." + processName);
    assertThat(
            span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("runId")))
            .isEqualTo(runId);
    assertThat(span.getAttributes()
            .get(io.opentelemetry.api.common.AttributeKey.stringKey("processName")))
            .isEqualTo(processName);
    assertThat(span.getAttributes()
            .get(io.opentelemetry.api.common.AttributeKey.stringKey("executionMode")))
            .isEqualTo(cbs.nova.dsl.ExecutionMode.RUN.name());
    assertThat(
            span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("status")))
            .isEqualTo(DslRunStatus.FAILED.name());
    assertThat(span.getStatus().getStatusCode())
            .isEqualTo(io.opentelemetry.api.trace.StatusCode.ERROR);
  }

  @Test
  void disabledTracingUsesNoOpOpenTelemetry() {
    ContextFactory contextFactory = Mockito.mock(ContextFactory.class);
    Mockito.when(contextFactory.generateRunId()).thenReturn("run-trace-2");
    Mockito.when(contextFactory.of(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
            .thenReturn(new SimpleContext<>(
                    Map.of(), Map.of(), cbs.nova.dsl.ExecutionMode.RUN,
                    "run-trace-2", cbs.nova.dsl.transaction.TransactionRouting.LOCAL, null, null,
                    null));

    TemporalDslProcessService service = new TemporalDslProcessService(
            contextFactory,
            new InMemoryDslRunRepository(),
            new ObjectMapper(),
            synchronousExecutor(),
            disabledScheduledExecutor(),
            Duration.ofSeconds(30),
            Duration.ofMinutes(5),
            false);
    // Default OpenTelemetry is no-op and must remain so.
    assertThat(service.getOpenTelemetry()).isSameAs(OpenTelemetry.noop());

    service.startProcess("missing-process", Map.of(), Map.of()).result().join();

    SpanContext spanContext = io.opentelemetry.api.trace.Span.current().getSpanContext();
    assertThat(spanContext.isValid()).isFalse();
  }

  private static ThreadPoolTaskExecutor synchronousExecutor() {
    ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor() {
      @Override
      public void execute(Runnable command) {
        command.run();
      }
    };
    exec.setCorePoolSize(1);
    exec.setMaxPoolSize(1);
    exec.setQueueCapacity(0);
    exec.setThreadNamePrefix("cbs-nova-dsl-trace-sync-");
    exec.initialize();
    return exec;
  }

  private static ScheduledExecutorService disabledScheduledExecutor() {
    ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "cbs-nova-dsl-healthcheck-trace-disabled");
      t.setDaemon(true);
      return t;
    });
    exec.shutdownNow();
    return exec;
  }
}
