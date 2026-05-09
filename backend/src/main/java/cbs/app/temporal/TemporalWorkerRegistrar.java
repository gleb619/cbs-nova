package cbs.app.temporal;

import cbs.app.temporal.massop.MassOpItemActivityImpl;
import cbs.app.temporal.massop.MassOpWorkflow;
import cbs.app.temporal.massop.MassOpWorkflowImpl;
import cbs.app.temporal.workflow.EventWorkflow;
import cbs.app.temporal.workflow.GenericEventWorkflowImpl;
import cbs.nova.registry.DslRegistry;
import cbs.nova.repository.EventExecutionRepository;
import cbs.nova.repository.MassOperationExecutionRepository;
import cbs.nova.repository.MassOperationItemRepository;
import cbs.nova.repository.WorkflowExecutionRepository;
import cbs.nova.repository.WorkflowTransitionLogRepository;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Registers Temporal workflow and activity implementations with the worker.
 *
 * <p>Generated event workflows are discovered via {@link GeneratedWorkflowRegistry} and registered
 * automatically. A generic fallback workflow handles events without generated Temporal-specific
 * implementations. Mass-operation workflows and all activity implementations are registered
 * explicitly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
// TODO: remove
@Deprecated(forRemoval = true)
public class TemporalWorkerRegistrar implements ApplicationRunner {

  private final WorkerFactory workerFactory;
  private final DslRegistry dslRegistry;
  private final WorkflowExecutionRepository workflowExecutionRepository;
  private final EventExecutionRepository eventExecutionRepository;
  private final WorkflowTransitionLogRepository transitionLogRepository;
  private final MassOperationExecutionRepository massOpExecutionRepository;
  private final MassOperationItemRepository massOpItemRepository;
  private final MassOpItemActivityImpl massOpItemActivityImpl;

  @Value("${app.temporal.task-queue}")
  private String taskQueue;

  @Override
  public void run(ApplicationArguments args) {
    Worker worker = workerFactory.newWorker(taskQueue);

    // 1. Register generated event workflows (Layer 3 codegen)
    // GeneratedWorkflowRegistry.registerAll(worker);
    log.info("Registered generated event workflows via GeneratedWorkflowRegistry");

    // 2. Register generic event workflow as fallback for non-generated events
    worker.registerWorkflowImplementationFactory(
        EventWorkflow.class,
        () -> new GenericEventWorkflowImpl(
            dslRegistry,
            workflowExecutionRepository,
            eventExecutionRepository,
            transitionLogRepository));
    log.info("Registered generic event workflow (fallback)");

    // 3. Register generated activities and mass-operation workflow
    // GeneratedActivityRegistry.registerAll(worker); // removed — use SpecDefinitionRegistry via
    // ActivityManager
    worker.registerWorkflowImplementationFactory(
        MassOpWorkflow.class,
        () -> new MassOpWorkflowImpl(
            dslRegistry, massOpExecutionRepository, massOpItemRepository, new ObjectMapper()));
    worker.registerActivitiesImplementations(massOpItemActivityImpl);

    workerFactory.start();
    log.info("Started Temporal worker on task queue: {}", taskQueue);
  }
}
