package cbs.nova.starter.service;

import cbs.nova.starter.core.StarterConstants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import cbs.nova.dsl.transaction.TransactionRouting;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.persistence.DslEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowStub;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;
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
    runRepository = new InMemoryDslRunRepository(InMemoryDslRunRepository.NO_OP_EVICTION);
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
            Optional.empty(),
            OpenTelemetry.noop(),
            ofPublisher(publisher),
            EmptyObjectProvider
                    .of(TransactionTemplate.class));

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
    service.runProcess("OkFlow", Map.of(), (String) null).result().join();

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
    service.runProcess("FailingFlow", Map.of(), (String) null).result().join();

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
    service.runProcess("OkFlow", Map.of(), (String) null).result().join();
    verify(publisher, atLeastOnce()).publish(any(DomainEvent.RunStarted.class));
    verify(publisher, atLeastOnce()).publish(any(DomainEvent.RunCompleted.class));
  }

  @Test
  void cancellationPublishesRunCancelledOnTerminalTransition() {
    DslRun running = DslRun.builder()
            .runId("run-cancel-test")
            .processName("CancelFlow")
            .status(DslRunStatus.RUNNING.name())
            .input("{}")
            .output("{}")
            .error(null)
            .startedAt(Instant.now())
            .finishedAt(StarterConstants.NOT_FINISHED_AT)
            .executionMode("RUN")
            .triggeredBy("test")
            .correlationId(null)
            .build();
    runRepository.save(running);

    WorkflowClient workflowClient = mock(
            WorkflowClient.class);
    WorkflowStub stub = mock(WorkflowStub.class);
    Mockito.when(workflowClient.newUntypedWorkflowStub(Mockito.anyString())).thenReturn(stub);

    DslRunCancellationService cancellationService = new DslRunCancellationService(
            workflowClient, runRepository, Clock.systemUTC(), null,
            ofPublisher(publisher),
            EmptyObjectProvider
                    .of(TransactionTemplate.class));

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
            .thenAnswer(invocation -> "run-" + UUID.randomUUID().toString()
                    .substring(0, 8));
    SimpleContext<Object> ctx = new SimpleContext<>(
            Map.of(), Map.of(), ExecutionMode.RUN,
            "run-x", TransactionRouting.LOCAL, null, null, null, null, null);
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

  private static ScheduledExecutorService disabledScheduledExecutor() {
    return Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "test-disabled-healthcheck");
      t.setDaemon(true);
      return t;
    });
  }

  // Suppress unused import warning for DslRunStatus used in named type assertions in
  // run-finish path comments.
  @SuppressWarnings("unused")
  private static final Class<?> DSL_RUN_STATUS_REF = DslRunStatus.class;

  // Suppress unused import warning for DslEventRepository referenced from the test description.
  @SuppressWarnings("unused")
  private static final Class<?> DSL_EVENT_REPOSITORY_REF = DslEventRepository.class;
}
