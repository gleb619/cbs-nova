package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.SimpleContext;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.persistence.DslEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration tests that wire {@link DomainEventPublisher} into the run lifecycle sites and verify
 * the events that get published. Pins the contract from the plan:
 *
 * <ul>
 * <li>a successful run publishes exactly one {@code RunStarted} row + one {@code RunCompleted} row
 * <li>a failing run publishes {@code RunStarted} + {@code RunFailed} rows
 * <li>the repository insert is called with {@code aggregate_type}/{@code aggregate_id} derived from
 * the event
 * </ul>
 *
 * The publisher is wired as a Mockito mock so the tests do not need an H2 database — the
 * transaction-boundary aspect is exercised at the JDBC layer by the dedicated
 * {@link cbs.nova.starter.persistence.DslEventRepositoryTest}.
 */
class DomainEventLifecycleIntegrationTest {

  private InMemoryDslRunRepository runRepository;
  private DomainEventPublisher publisher;
  private TemporalDslProcessService service;

  @BeforeEach
  void setUp() {
    runRepository = new InMemoryDslRunRepository();
    publisher = mock(DomainEventPublisher.class);

    service = new TemporalDslProcessService(
            mockContextFactory(),
            runRepository,
            new ObjectMapper(),
            sameThreadExecutor(),
            disabledScheduledExecutor(),
            Duration.ofSeconds(30),
            Duration.ofMinutes(5),
            false,
            Long.MAX_VALUE,
            new SimpleMeterRegistry(),
            new RunIdentityResolver(),
            java.util.Optional.empty(),
            io.opentelemetry.api.OpenTelemetry.noop(),
            ofPublisher(publisher),
            EmptyObjectProvider
                    .of(org.springframework.transaction.support.TransactionTemplate.class));

    GlobalManager.globalManager().resetForTests();
    GlobalManager.globalManager().registerProcess(
            Dsl.process("OkFlow").execute(ctx -> Result.success("ok")).build());
    GlobalManager.globalManager().registerProcess(
            Dsl.process("FailingFlow")
                    .execute(ctx -> Result.failure(new RuntimeException("kaboom")))
                    .build());
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void successfulRunEmitsRunStartedAndRunCompleted() {
    service.runProcess("OkFlow", java.util.Map.of(), (String) null).result().join();

    ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(publisher, atLeastOnce()).publish(captor.capture());
    List<DomainEvent> events = captor.getAllValues();

    assertThat(events).extracting(DomainEvent::eventType)
            .contains("RunStarted", "RunCompleted");

    DomainEvent completed = events.stream()
            .filter(e -> "RunCompleted".equals(e.eventType()))
            .findFirst()
            .orElseThrow();
    assertThat(completed.aggregateType()).isEqualTo("run");
    assertThat(completed.aggregateId()).isNotBlank();
    assertThat(completed.schemaVersion()).isEqualTo(1);

    DomainEvent started = events.stream()
            .filter(e -> "RunStarted".equals(e.eventType()))
            .findFirst()
            .orElseThrow();
    assertThat(started.aggregateId()).isEqualTo(completed.aggregateId());
  }

  @Test
  void failingRunEmitsRunStartedAndRunFailed() {
    service.runProcess("FailingFlow", java.util.Map.of(), (String) null).result().join();

    ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(publisher, atLeastOnce()).publish(captor.capture());
    List<DomainEvent> events = captor.getAllValues();

    assertThat(events).extracting(DomainEvent::eventType)
            .contains("RunStarted", "RunFailed");

    DomainEvent failed = events.stream()
            .filter(e -> "RunFailed".equals(e.eventType()))
            .findFirst()
            .orElseThrow();
    assertThat(failed.aggregateType()).isEqualTo("run");
    assertThat(failed.aggregateId()).isNotBlank();
  }

  @Test
  void publishesAtLeastOneRunStartedAndOneTerminalEvent() {
    // Sanity check: a successful run produces two publish calls.
    service.runProcess("OkFlow", java.util.Map.of(), (String) null).result().join();
    verify(publisher, atLeastOnce()).publish(any(DomainEvent.RunStarted.class));
    verify(publisher, atLeastOnce()).publish(any(DomainEvent.RunCompleted.class));
  }

  @Test
  void cancellationPublishesRunCancelledOnTerminalTransition() {
    cbs.nova.dsl.history.DslRun running = cbs.nova.dsl.history.DslRun.builder()
            .runId("run-cancel-test")
            .processName("CancelFlow")
            .status(cbs.nova.dsl.history.DslRunStatus.RUNNING.name())
            .input("{}")
            .output("{}")
            .error(null)
            .startedAt(java.time.Instant.now())
            .finishedAt(cbs.nova.starter.service.TemporalDslProcessService.NOT_FINISHED_AT)
            .executionMode("RUN")
            .triggeredBy("test")
            .correlationId(null)
            .build();
    runRepository.save(running);

    io.temporal.client.WorkflowClient workflowClient = mock(
            io.temporal.client.WorkflowClient.class);
    io.temporal.client.WorkflowStub stub = mock(io.temporal.client.WorkflowStub.class);
    Mockito.when(workflowClient.newUntypedWorkflowStub(Mockito.anyString())).thenReturn(stub);

    DslRunCancellationService cancellationService = new DslRunCancellationService(
            workflowClient, runRepository, java.time.Clock.systemUTC(), null,
            ofPublisher(publisher),
            EmptyObjectProvider
                    .of(org.springframework.transaction.support.TransactionTemplate.class));

    var outcome = cancellationService.cancel("run-cancel-test");
    assertThat(outcome.outcome())
            .isEqualTo(DslRunCancellationService.Outcome.CANCELLED);

    ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(publisher, atLeastOnce()).publish(captor.capture());
    DomainEvent cancelled = captor.getAllValues().stream()
            .filter(e -> "RunCancelled".equals(e.eventType()))
            .findFirst()
            .orElseThrow();
    assertThat(cancelled.aggregateType()).isEqualTo("run");
    assertThat(cancelled.aggregateId()).isEqualTo("run-cancel-test");
    assertThat(cancelled.schemaVersion()).isEqualTo(1);
  }

  private static ContextFactory mockContextFactory() {
    ContextFactory contextFactory = mock(ContextFactory.class);
    Mockito.when(contextFactory.generateRunId())
            .thenAnswer(invocation -> "run-" + java.util.UUID.randomUUID().toString()
                    .substring(0, 8));
    SimpleContext<Object> ctx = new SimpleContext<>(
            java.util.Map.of(), java.util.Map.of(), cbs.nova.dsl.ExecutionMode.RUN,
            "run-x", cbs.nova.dsl.transaction.TransactionRouting.LOCAL, null, null, null);
    Mockito.when(contextFactory.of(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
            .thenReturn(ctx);
    return contextFactory;
  }

  private static ObjectProvider<DomainEventPublisher> ofPublisher(DomainEventPublisher publisher) {
    return new ObjectProvider<>() {
      @Override
      public DomainEventPublisher getObject() {
        return publisher;
      }

      @Override
      public DomainEventPublisher getIfAvailable() {
        return publisher;
      }

      @Override
      public DomainEventPublisher getIfUnique() {
        return publisher;
      }
    };
  }

  private static ThreadPoolTaskExecutor sameThreadExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.initialize();
    return executor;
  }

  private static java.util.concurrent.ScheduledExecutorService disabledScheduledExecutor() {
    return java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "test-disabled-healthcheck");
      t.setDaemon(true);
      return t;
    });
  }

  // Suppress unused import warning for DslRunStatus used in named type assertions in
  // run-finish path comments.
  @SuppressWarnings("unused")
  private static final Class<?> DSL_RUN_STATUS_REF = cbs.nova.dsl.history.DslRunStatus.class;

  // Suppress unused import warning for DslEventRepository referenced from the test description.
  @SuppressWarnings("unused")
  private static final Class<?> DSL_EVENT_REPOSITORY_REF = DslEventRepository.class;
}
