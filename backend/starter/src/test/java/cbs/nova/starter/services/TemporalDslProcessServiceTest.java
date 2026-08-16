package cbs.nova.starter.services;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.SimpleContext;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.exception.DslEntityNotFoundException;
import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import cbs.nova.dsl.transaction.TransactionRouting;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.sentry.Sentry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class TemporalDslProcessServiceTest {

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  private static ContextFactory mockContextFactoryWith(String runId,
          SimpleContext<Object> ctx) {
    ContextFactory contextFactory = Mockito.mock(ContextFactory.class);
    Mockito.when(contextFactory.generateRunId()).thenReturn(runId);
    Mockito.doReturn(ctx).when(contextFactory)
            .of(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    return contextFactory;
  }

  private static TemporalDslProcessService newService(ContextFactory contextFactory) {
    return new TemporalDslProcessService(
            contextFactory, new InMemoryDslRunRepository(), new ObjectMapper());
  }

  @Test
  void runProcessSingleArgDefaultsMetadataToEmptyMap() {
    SimpleContext<Object> stubCtx = new SimpleContext<>("payload", Map.of(), ExecutionMode.RUN,
            "run-id-1", TransactionRouting.LOCAL, null, null, null);
    ContextFactory contextFactory = mockContextFactoryWith("run-id-1", stubCtx);

    newService(contextFactory).runProcess(unique(), "payload");

    Mockito.verify(contextFactory).of(
            Mockito.eq("payload"),
            Mockito.eq(Map.of()),
            Mockito.eq(ExecutionMode.RUN),
            Mockito.eq("run-id-1"));
  }

  @Test
  void startProcessThreeArgCoercesNullInputToEmptyMap() {
    SimpleContext<Object> stubCtx = new SimpleContext<>(Map.of(), Map.of("k", "v"),
            ExecutionMode.RUN, "run-id-2", TransactionRouting.LOCAL, null, null, null);
    ContextFactory contextFactory = mockContextFactoryWith("run-id-2", stubCtx);

    newService(contextFactory).startProcess(unique(), null, Map.of("k", "v"));

    ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
    Mockito.verify(contextFactory).of(
            bodyCaptor.capture(),
            Mockito.eq(Map.of("k", "v")),
            Mockito.eq(ExecutionMode.RUN),
            Mockito.eq("run-id-2"));
    assertThat(bodyCaptor.getValue()).isEqualTo(Map.of());
  }

  @Test
  void startProcessUsesRunIdGeneratedByContextFactory() {
    SimpleContext<Object> stubCtx = new SimpleContext<>("payload", Map.of(), ExecutionMode.RUN,
            "run-id-3", TransactionRouting.LOCAL, null, null, null);
    ContextFactory contextFactory = mockContextFactoryWith("run-id-3", stubCtx);

    newService(contextFactory).startProcess(unique(), "payload", Map.of());

    Mockito.verify(contextFactory).generateRunId();
    Mockito.verify(contextFactory).of(
            Mockito.any(),
            Mockito.any(),
            Mockito.eq(ExecutionMode.RUN),
            Mockito.eq("run-id-3"));
  }

  @Test
  void startProcessReachesGlobalManagerWithCorrectProcessName() {
    String missing = "missing-" + UUID.randomUUID();
    TemporalDslProcessService service = new TemporalDslProcessService(new ContextFactory(),
            new InMemoryDslRunRepository(), new ObjectMapper());

    Result<?> result = service.startProcess(missing, Map.of("k", "v"), Map.of("meta", "data"))
            .result()
            .join();

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(DslEntityNotFoundException.class);
    assertThat(result.cause()).hasMessageContaining("Process not found: " + missing);
  }

  @Test
  void startContextCarriesNonEmptyInputThroughToContextFactory() {
    Map<String, Object> input = Map.of("a", 1, "b", "two");
    SimpleContext<Object> stubCtx = new SimpleContext<>(input, Map.of(), ExecutionMode.RUN,
            "run-id-4", TransactionRouting.LOCAL, null, null, null);
    ContextFactory contextFactory = mockContextFactoryWith("run-id-4", stubCtx);

    newService(contextFactory).startProcess(unique(), input, Map.of("meta", "data"));

    Mockito.verify(contextFactory).of(
            Mockito.eq(input),
            Mockito.eq(Map.of("meta", "data")),
            Mockito.eq(ExecutionMode.RUN),
            Mockito.eq("run-id-4"));
  }

  @Test
  void startProcessPropagatesRunIdToMdcAndSentry() {
    SimpleContext<Object> stubCtx = new SimpleContext<>("payload", Map.of(), ExecutionMode.RUN,
            "run-id-5", TransactionRouting.LOCAL, null, null, null);
    ContextFactory contextFactory = mockContextFactoryWith("run-id-5", stubCtx);

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
      service.startProcess(unique(), "payload", Map.of());

      assertThat(MDC.get("runId")).isNull();
      sentry.verify(() -> Sentry.setTag("runId", "run-id-5"));
    }
  }

  @Test
  void staleHealthcheckTransitionsRunningRunToStale() throws Exception {
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    try {
      ThreadPoolTaskExecutor exec = synchronousExecutor();
      InMemoryDslRunRepository repo = new InMemoryDslRunRepository();
      longAgoClock fixedClock = new longAgoClock();

      ContextFactory contextFactory = Mockito.mock(ContextFactory.class);
      Mockito.when(contextFactory.generateRunId()).thenReturn("run-stale-1");
      TemporalDslProcessService service = new TemporalDslProcessService(
              contextFactory, repo, new ObjectMapper(),
              exec, scheduler, Duration.ofMillis(1), Duration.ofMillis(100), false);
      service.setClock(fixedClock);

      // Persist a running run whose startedAt is far older than the 100ms staleness threshold.
      repo.save(DslRun.builder()
              .runId("run-stale-1")
              .processName("ghost-process")
              .status(DslRunStatus.RUNNING.name())
              .input("{}")
              .output("{}")
              .error(null)
              .startedAt(fixedClock.instant().minus(Duration.ofMinutes(10)))
              .finishedAt(Instant.EPOCH)
              .executionMode("RUN")
              .build());

      service.ensureHealthcheckForTest();

      // Drive the scheduled sweep until the row flips to STALE or the deadline elapses.
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      DslRunStatus status = DslRunStatus.RUNNING;
      while (System.nanoTime() < deadline) {
        status = DslRunStatus.valueOf(repo.findByRunId("run-stale-1").orElseThrow().status());
        if (status == DslRunStatus.STALE) {
          break;
        }
        Thread.sleep(20);
      }

      assertThat(status).isEqualTo(DslRunStatus.STALE);
    } finally {
      scheduler.shutdownNow();
    }
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
    exec.setThreadNamePrefix("cbs-nova-dsl-test-sync-");
    exec.initialize();
    return exec;
  }

  /**
   * {@link Clock} that returns a fixed instant so the staleness detector deterministically
   * classifies the seeded run as overdue.
   */
  private static final class longAgoClock extends Clock {

    private final Instant instant = Instant.parse("2026-01-01T00:00:00Z");

    @Override
    public Instant instant() {
      return instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }

  private static String unique() {
    return "proc-" + UUID.randomUUID();
  }
}
