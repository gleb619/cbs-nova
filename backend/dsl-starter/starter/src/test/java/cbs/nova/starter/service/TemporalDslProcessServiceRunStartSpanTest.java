package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * T746: asserts that {@link TemporalDslProcessService#startProcess} emits a top-level
 * {@code dsl.run.start} span carrying {@code runId}, {@code processName}, and {@code correlationId}
 * attributes, separate from the inner {@code dsl.run.<processName>} span created by the executor.
 * The inner span is asserted to be a child of the start span so Jaeger joins the operator's start
 * click to its workflow execution.
 */
class TemporalDslProcessServiceRunStartSpanTest {

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void startProcessEmitsStartSpanWrappingInnerExecutionSpan() {
    InMemorySpanExporter exporter = InMemorySpanExporter.create();
    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build();
    OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();

    String runId = "run-t746-1";
    String processName = "missing-process";
    String correlationId = "cid-t746-1";

    try (MockedStatic<SimpleContext> simple = Mockito.mockStatic(SimpleContext.class,
            Mockito.CALLS_REAL_METHODS)) {
      simple.when(SimpleContext::generateRunId).thenReturn(runId);
      TemporalDslProcessService service = new TemporalDslProcessService(
              new InMemoryDslRunRepository(InMemoryDslRunRepository.NO_OP_EVICTION),
              new ObjectMapper(),
              synchronousExecutor(),
              disabledScheduledExecutor(),
              Duration.ofSeconds(30),
              Duration.ofMinutes(5),
              false,
              Long.MAX_VALUE,
              new SimpleMeterRegistry(),
              nullResolver(),
              Optional.empty(),
              openTelemetry,
              EmptyObjectProvider.of(DomainEventPublisher.class),
              EmptyObjectProvider.of(TransactionTemplate.class));

      service.startProcess(processName, Map.of(), Map.of(), correlationId).result().join();

      List<SpanData> finished = exporter.getFinishedSpanItems();
      SpanData startSpan = findSpan(finished, "dsl.run.start");
      SpanData runSpan = findSpan(finished, "dsl.run." + processName);

      assertThat(startSpan).as("top-level dsl.run.start span").isNotNull();
      assertThat(startSpan.getAttributes().get(AttributeKey.stringKey("runId")))
              .isEqualTo(runId);
      assertThat(startSpan.getAttributes().get(AttributeKey.stringKey("processName")))
              .isEqualTo(processName);
      assertThat(startSpan.getAttributes().get(AttributeKey.stringKey("correlationId")))
              .isEqualTo(correlationId);

      // Inner execution span is a child of the start span.
      assertThat(runSpan).as("inner dsl.run.<process> span").isNotNull();
      assertThat(runSpan.getParentSpanId()).isEqualTo(startSpan.getSpanContext().getSpanId());
    }
  }

  @Test
  void startProcessStartSpanOmitsCorrelationIdWhenAbsent() {
    InMemorySpanExporter exporter = InMemorySpanExporter.create();
    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build();
    OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();

    String runId = "run-t746-2";

    try (MockedStatic<SimpleContext> simple = Mockito.mockStatic(SimpleContext.class,
            Mockito.CALLS_REAL_METHODS)) {
      simple.when(SimpleContext::generateRunId).thenReturn(runId);
      TemporalDslProcessService service = new TemporalDslProcessService(
              new InMemoryDslRunRepository(InMemoryDslRunRepository.NO_OP_EVICTION),
              new ObjectMapper(),
              synchronousExecutor(),
              disabledScheduledExecutor(),
              Duration.ofSeconds(30),
              Duration.ofMinutes(5),
              false,
              Long.MAX_VALUE,
              new SimpleMeterRegistry(),
              nullResolver(),
              Optional.empty(),
              openTelemetry,
              EmptyObjectProvider.of(DomainEventPublisher.class),
              EmptyObjectProvider.of(TransactionTemplate.class));

      service.startProcess("missing-process", Map.of(), Map.of()).result().join();

      SpanData startSpan = findSpan(exporter.getFinishedSpanItems(), "dsl.run.start");
      assertThat(startSpan).isNotNull();
      // Empty string is the documented stand-in when no correlationId was supplied — keeps the
      // attribute present so Jaeger queries don't need a null branch.
      assertThat(startSpan.getAttributes().get(AttributeKey.stringKey("correlationId")))
              .isEqualTo("");
    }
  }

  private static SpanData findSpan(List<SpanData> spans, String name) {
    return spans.stream().filter(s -> name.equals(s.getName())).findFirst().orElse(null);
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
    exec.setThreadNamePrefix("cbs-nova-dsl-trace-start-");
    exec.initialize();
    return exec;
  }

  private static ScheduledExecutorService disabledScheduledExecutor() {
    ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "cbs-nova-dsl-healthcheck-t746-disabled");
      t.setDaemon(true);
      return t;
    });
    exec.shutdownNow();
    return exec;
  }

  private static RunIdentityResolver nullResolver() {
    return new RunIdentityResolver() {
      @Override
      public String resolve() {
        return null;
      }
    };
  }
}
