package cbs.app.temporal;

import cbs.app.temporal.activity.TransactionActivityImpl;
import cbs.app.temporal.massop.MassOpItemActivityImpl;
import cbs.app.temporal.massop.MassOpWorkflowImpl;
import cbs.app.temporal.workflow.EventWorkflowImpl;
import cbs.dsl.runtime.DslRegistry;
import cbs.nova.repository.EventExecutionRepository;
import cbs.nova.repository.MassOperationExecutionRepository;
import cbs.nova.repository.MassOperationItemRepository;
import cbs.nova.repository.WorkflowExecutionRepository;
import cbs.nova.repository.WorkflowTransitionLogRepository;
import cbs.nova.temporal.workflow.EventWorkflow;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class TemporalWorkerRegistrar implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(TemporalWorkerRegistrar.class);
  private final WorkerFactory workerFactory;
  private final DslRegistry dslRegistry;
  private final WorkflowExecutionRepository workflowExecutionRepository;
  private final EventExecutionRepository eventExecutionRepository;
  private final WorkflowTransitionLogRepository transitionLogRepository;
  private final TransactionActivityImpl transactionActivityImpl;
  private final MassOperationExecutionRepository massOpExecutionRepository;
  private final MassOperationItemRepository massOpItemRepository;
  private final MassOpItemActivityImpl massOpItemActivityImpl;

  @Value("${temporal.task-queue:WORKFLOW_TASK_QUEUE}")
  private String taskQueue;

  public TemporalWorkerRegistrar(
      WorkerFactory workerFactory,
      DslRegistry dslRegistry,
      WorkflowExecutionRepository workflowExecutionRepository,
      EventExecutionRepository eventExecutionRepository,
      WorkflowTransitionLogRepository transitionLogRepository,
      TransactionActivityImpl transactionActivityImpl,
      MassOperationExecutionRepository massOpExecutionRepository,
      MassOperationItemRepository massOpItemRepository,
      MassOpItemActivityImpl massOpItemActivityImpl) {
    this.workerFactory = workerFactory;
    this.dslRegistry = dslRegistry;
    this.workflowExecutionRepository = workflowExecutionRepository;
    this.eventExecutionRepository = eventExecutionRepository;
    this.transitionLogRepository = transitionLogRepository;
    this.transactionActivityImpl = transactionActivityImpl;
    this.massOpExecutionRepository = massOpExecutionRepository;
    this.massOpItemRepository = massOpItemRepository;
    this.massOpItemActivityImpl = massOpItemActivityImpl;
  }

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
        cbs.app.temporal.massop.MassOpWorkflow.class,
        () -> new MassOpWorkflowImpl(dslRegistry, massOpExecutionRepository, massOpItemRepository));
    worker.registerActivitiesImplementations(massOpItemActivityImpl);
    workerFactory.start();
    LOG.info("Started Temporal worker on task queue: {}", taskQueue);
  }
}
