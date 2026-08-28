package cbs.nova.starter.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import cbs.nova.starter.service.DslRunCancellationService;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;

/**
 * Verifies that {@link DslRunCancellationService} really terminates a long-running Temporal
 * workflow against a live Temporal server, not just a mocked client.
 */
@Testcontainers
class DslRunCancellationIntegrationTest {

  private static final String TASK_QUEUE = "dsl-run-cancellation-queue";

  @Container
  private static final GenericContainer<?> TEMPORAL = new GenericContainer<>(
          DockerImageName.parse("temporalio/auto-setup:1.25.2"))
          .withExposedPorts(7233)
          .withEnv("DB", "sqlite")
          .withEnv("BIND_ON_IP", "0.0.0.0")
          .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("temporal"))
          .withCommand("server", "start-dev",
                  "--ip", "0.0.0.0",
                  "--namespace", "default",
                  "--db-filename", "/tmp/temporal.db")
          .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)));

  @WorkflowInterface
  public interface SleepingWorkflow {

    @WorkflowMethod
    String execute();
  }

  public static class SleepingWorkflowImpl implements SleepingWorkflow {

    @Override
    public String execute() {
      Workflow.sleep(Duration.ofMinutes(30));
      return "never-reached";
    }
  }

  private static WorkflowServiceStubs serviceStubs;
  private static WorkerFactory workerFactory;
  private static WorkflowClient workflowClient;

  @BeforeAll
  static void startWorker() {
    serviceStubs = WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                    .setTarget(TEMPORAL.getHost() + ":" + TEMPORAL.getMappedPort(7233))
                    .build());
    workflowClient = WorkflowClient.newInstance(serviceStubs);
    workerFactory = WorkerFactory.newInstance(workflowClient);
    Worker worker = workerFactory.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(SleepingWorkflowImpl.class);
    workerFactory.start();
  }

  @AfterAll
  static void stopWorker() {
    if (workerFactory != null) {
      workerFactory.shutdownNow();
    }
    if (serviceStubs != null) {
      serviceStubs.shutdownNow();
    }
  }

  @Test
  void cancelStopsALiveWorkflowAndRecordsCancelledStatus() {
    String runId = "cancel-it-" + System.currentTimeMillis();
    InMemoryDslRunRepository repository = new InMemoryDslRunRepository();
    repository.save(DslRun.builder()
            .runId(runId)
            .processName("SleepingProcess")
            .status(DslRunStatus.RUNNING.name())
            .startedAt(Instant.now())
            .executionMode("RUN")
            .build());

    SleepingWorkflow stub = workflowClient.newWorkflowStub(SleepingWorkflow.class,
            WorkflowOptions.newBuilder()
                    .setTaskQueue(TASK_QUEUE)
                    .setWorkflowId(runId)
                    .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                    .build());
    WorkflowClient.start(stub::execute);

    DslRunCancellationService service = new DslRunCancellationService(workflowClient, repository);

    DslRunCancellationService.CancelResult result = service.cancel(runId);

    assertThat(result.outcome()).isEqualTo(DslRunCancellationService.Outcome.CANCELLED);

    DslRun stored = repository.findByRunId(runId).orElseThrow();
    assertThat(stored.status()).isEqualTo(DslRunStatus.CANCELLED.name());
    assertThat(stored.error()).isEqualTo(DslRunCancellationService.CANCELLED_REASON);
    assertThat(stored.finishedAt()).isNotNull();

    assertThatThrownBy(() -> WorkflowStub.fromTyped(stub).getResult(String.class))
            .isInstanceOf(WorkflowFailedException.class);
  }

  @Test
  void cancelIsAConflictOnceTheRunIsNoLongerRunning() {
    String runId = "already-done-" + System.currentTimeMillis();
    InMemoryDslRunRepository repository = new InMemoryDslRunRepository();
    repository.save(DslRun.builder()
            .runId(runId)
            .processName("SleepingProcess")
            .status(DslRunStatus.COMPLETED.name())
            .startedAt(Instant.now())
            .finishedAt(Instant.now())
            .executionMode("RUN")
            .build());

    DslRunCancellationService service = new DslRunCancellationService(workflowClient, repository);

    DslRunCancellationService.CancelResult result = service.cancel(runId);

    assertThat(result.outcome()).isEqualTo(DslRunCancellationService.Outcome.NOT_CANCELLABLE);
    assertThat(result.currentStatus()).isEqualTo(DslRunStatus.COMPLETED.name());
    assertThat(repository.findByRunId(runId).orElseThrow().status())
            .isEqualTo(DslRunStatus.COMPLETED.name());
  }
}
