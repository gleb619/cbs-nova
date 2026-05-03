package cbs.app.temporal;

import cbs.app.temporal.activity.TransactionActivityImpl;
import cbs.app.temporal.massop.MassOpItemActivityImpl;
import cbs.app.temporal.massop.MassOpWorkflow;
import cbs.app.temporal.massop.MassOpWorkflowImpl;
import cbs.app.temporal.workflow.EventWorkflowImpl;
import cbs.dsl.runtime.DslRegistry;
import cbs.nova.repository.EventExecutionRepository;
import cbs.nova.repository.MassOperationExecutionRepository;
import cbs.nova.repository.MassOperationItemRepository;
import cbs.nova.repository.WorkflowExecutionRepository;
import cbs.nova.repository.WorkflowTransitionLogRepository;
import cbs.nova.service.EventWorkflow;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class TemporalWorkerRegistrar implements ApplicationRunner {

  private final WorkerFactory workerFactory;
  private final DslRegistry dslRegistry;
  private final WorkflowExecutionRepository workflowExecutionRepository;
  private final EventExecutionRepository eventExecutionRepository;
  private final WorkflowTransitionLogRepository transitionLogRepository;
  private final TransactionActivityImpl transactionActivityImpl;
  private final MassOperationExecutionRepository massOpExecutionRepository;
  private final MassOperationItemRepository massOpItemRepository;
  private final MassOpItemActivityImpl massOpItemActivityImpl;

  @Value("${app.temporal.task-queue}")
  private String taskQueue;

  @Override
  public void run(ApplicationArguments args) {
    Worker worker = workerFactory.newWorker(taskQueue);
    worker.registerWorkflowImplementationFactory(
        EventWorkflow.class,
        () -> new EventWorkflowImpl(
            dslRegistry,
            workflowExecutionRepository,
            eventExecutionRepository,
            transitionLogRepository));
    worker.registerActivitiesImplementations(transactionActivityImpl);
    worker.registerWorkflowImplementationFactory(
        MassOpWorkflow.class,
        () -> new MassOpWorkflowImpl(
            dslRegistry, massOpExecutionRepository, massOpItemRepository, new ObjectMapper()));
    worker.registerActivitiesImplementations(massOpItemActivityImpl);
    workerFactory.start();
    log.info("Started Temporal worker on task queue: {}", taskQueue);
  }
}
