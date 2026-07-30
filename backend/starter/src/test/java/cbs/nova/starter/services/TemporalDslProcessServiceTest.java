package cbs.nova.starter.services;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.DslEntityNotFoundException;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.SimpleContext;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.sentry.Sentry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.MDC;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

class TemporalDslProcessServiceTest {

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void runProcessSingleArgDefaultsMetadataToEmptyMap() {
    ContextFactory contextFactory = Mockito.mock(ContextFactory.class);
    Mockito.when(contextFactory.generateRunId()).thenReturn("run-id-1");
    SimpleContext<Object> stubCtx = new SimpleContext<>(
            "payload", Map.of(), ExecutionMode.RUN, "run-id-1");
    Mockito.doReturn(stubCtx).when(contextFactory).of(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

    TemporalDslProcessService service = new TemporalDslProcessService(
            contextFactory, new InMemoryDslRunRepository(), new ObjectMapper());
    service.runProcess(unique(), "payload");

    Mockito.verify(contextFactory).of(
            Mockito.eq("payload"),
            Mockito.eq(Map.of()),
            Mockito.eq(ExecutionMode.RUN),
            Mockito.eq("run-id-1"));
  }

  @Test
  void runProcessThreeArgCoercesNullInputToEmptyMap() {
    ContextFactory contextFactory = Mockito.mock(ContextFactory.class);
    Mockito.when(contextFactory.generateRunId()).thenReturn("run-id-2");
    SimpleContext<Object> stubCtx = new SimpleContext<>(
            Map.of(), Map.of("k", "v"), ExecutionMode.RUN, "run-id-2");
    Mockito.doReturn(stubCtx).when(contextFactory).of(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

    TemporalDslProcessService service = new TemporalDslProcessService(
            contextFactory, new InMemoryDslRunRepository(), new ObjectMapper());
    service.runProcess(unique(), null, Map.of("k", "v"));

    ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
    Mockito.verify(contextFactory).of(
            bodyCaptor.capture(),
            Mockito.eq(Map.of("k", "v")),
            Mockito.eq(ExecutionMode.RUN),
            Mockito.eq("run-id-2"));
    assertThat(bodyCaptor.getValue()).isEqualTo(Map.of());
  }

  @Test
  void runProcessUsesRunIdGeneratedByContextFactory() {
    ContextFactory contextFactory = Mockito.mock(ContextFactory.class);
    Mockito.when(contextFactory.generateRunId()).thenReturn("run-id-3");
    SimpleContext<Object> stubCtx = new SimpleContext<>(
            "payload", Map.of(), ExecutionMode.RUN, "run-id-3");
    Mockito.doReturn(stubCtx).when(contextFactory).of(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

    TemporalDslProcessService service = new TemporalDslProcessService(
            contextFactory, new InMemoryDslRunRepository(), new ObjectMapper());
    service.runProcess(unique(), "payload", Map.of());

    Mockito.verify(contextFactory).generateRunId();
    Mockito.verify(contextFactory).of(
            Mockito.any(),
            Mockito.any(),
            Mockito.eq(ExecutionMode.RUN),
            Mockito.eq("run-id-3"));
  }

  @Test
  void runProcessReachesGlobalManagerWithCorrectProcessName() {
    ContextFactory contextFactory = new ContextFactory();
    String missing = "missing-" + UUID.randomUUID();
    TemporalDslProcessService service = new TemporalDslProcessService(
            contextFactory, new InMemoryDslRunRepository(), new ObjectMapper());

    Result<?> result = service.runProcess(missing, Map.of("k", "v"), Map.of("meta", "data"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(DslEntityNotFoundException.class);
    assertThat(result.cause()).hasMessageContaining("Process not found: " + missing);
  }

  @Test
  void runContextCarriesNonEmptyInputThroughToContextFactory() {
    ContextFactory contextFactory = Mockito.mock(ContextFactory.class);
    Mockito.when(contextFactory.generateRunId()).thenReturn("run-id-4");
    Map<String, Object> input = Map.of("a", 1, "b", "two");
    SimpleContext<Object> stubCtx = new SimpleContext<>(
            input, Map.of(), ExecutionMode.RUN, "run-id-4");
    Mockito.doReturn(stubCtx).when(contextFactory).of(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

    TemporalDslProcessService service = new TemporalDslProcessService(
            contextFactory, new InMemoryDslRunRepository(), new ObjectMapper());
    service.runProcess(unique(), input, Map.of("meta", "data"));

    Mockito.verify(contextFactory).of(
            Mockito.eq(input),
            Mockito.eq(Map.of("meta", "data")),
            Mockito.eq(ExecutionMode.RUN),
            Mockito.eq("run-id-4"));
  }

  @Test
  void runProcessPropagatesRunIdToMdcAndSentry() {
    ContextFactory contextFactory = Mockito.mock(ContextFactory.class);
    Mockito.when(contextFactory.generateRunId()).thenReturn("run-id-5");
    SimpleContext<Object> stubCtx = new SimpleContext<>(
            "payload", Map.of(), ExecutionMode.RUN, "run-id-5");
    Mockito.doReturn(stubCtx).when(contextFactory).of(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

    try (MockedStatic<Sentry> sentry = Mockito.mockStatic(Sentry.class);
            MockedStatic<Baggage> baggage = Mockito.mockStatic(Baggage.class)) {
      Baggage current = Mockito.mock(Baggage.class);
      BaggageBuilder builder = Mockito.mock(BaggageBuilder.class);
      Baggage built = Mockito.mock(Baggage.class);
      Mockito.when(Baggage.current()).thenReturn(current);
      Mockito.when(current.toBuilder()).thenReturn(builder);
      Mockito.when(builder.put("runId", "run-id-5")).thenReturn(builder);
      Mockito.when(builder.build()).thenReturn(built);
      Mockito.when(built.makeCurrent()).thenReturn(() -> {
      });

      TemporalDslProcessService service = new TemporalDslProcessService(
              contextFactory, new InMemoryDslRunRepository(), new ObjectMapper());
      service.runProcess(unique(), "payload", Map.of());

      assertThat(MDC.get("runId")).isNull();
      sentry.verify(() -> Sentry.setTag("runId", "run-id-5"));
    }
  }

  private static String unique() {
    return "proc-" + UUID.randomUUID();
  }
}
