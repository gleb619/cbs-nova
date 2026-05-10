package cbs.nova.temporal;

import cbs.dsl.api.EventOperation;
import cbs.dsl.api.EventTypes.EventInput;
import cbs.dsl.api.SpecDefinitionRegistry;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.worker.Worker;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Client-side manager for working with generated Temporal workflows.
 *
 * <p>Typical usage from a Spring service (outside a workflow):
 *
 * <pre>
 *   MyWorkflow stub = workflowManager.newWorkflowStub("LOAN_DISBURSEMENT", "loan-123");
 *   stub.execute(request);
 * </pre>
 */
@RequiredArgsConstructor
public class WorkflowManager {

  private final SpecDefinitionRegistry artifactRegistry;
  private final WorkflowClient workflowClient;
  private final String taskQueue;

  /**
   * Creates a typed workflow stub for the given event code.
   *
   * @param code the event code
   * @param workflowId the unique workflow ID
   * @param <T> the workflow interface type
   * @return a typed Temporal workflow stub
   * @throws IllegalArgumentException if the code is not registered
   */
  @SuppressWarnings("unchecked")
  public <T extends EventOperation> T newWorkflowStub(String code, String workflowId) {
    Class<T> workflowInterface = artifactRegistry.getWorkflowInterface(code);

    String queue = taskQueue.isBlank() ? "cbs-nova-task-queue" : taskQueue;
    WorkflowOptions options = WorkflowOptions.newBuilder()
        .setWorkflowId(workflowId)
        .setTaskQueue(queue)
        .build();

    return workflowClient.newWorkflowStub(workflowInterface, options);
  }

  /**
   * Creates an untyped workflow stub for the given event code.
   *
   * @param code the event code
   * @param workflowId the unique workflow ID
   * @return an untyped Temporal workflow stub
   */
  public WorkflowStub newUntypedWorkflowStub(String code, String workflowId) {
    String queue = taskQueue.isBlank() ? "cbs-nova-task-queue" : taskQueue;
    WorkflowOptions options = WorkflowOptions.newBuilder()
        .setWorkflowId(workflowId)
        .setTaskQueue(queue)
        .build();

    return workflowClient.newUntypedWorkflowStub(code, options);
  }

  /**
   * Returns the workflow interface class registered under the given code.
   *
   * @param code the workflow code
   * @return the interface class
   */
  public <T extends EventOperation> Class<T> getWorkflowInterface(String code) {
    return artifactRegistry.getWorkflowInterface(code);
  }

  /**
   * Returns all registered workflow codes.
   *
   * @return unmodifiable set of workflow codes
   */
  public Set<String> getWorkflowCodes() {
    return artifactRegistry.getWorkflowCodes();
  }

  /**
   * Returns the generated workflow implementation for direct invocation (e.g. in preview mode).
   *
   * @param code the workflow code
   * @param workflowInterface the expected interface class
   * @param <T> the workflow type
   * @return the implementation instance
   */
  public <T extends EventOperation> T getWorkflow(String code, Class<T> workflowInterface) {
    return artifactRegistry.getWorkflow(code, workflowInterface);
  }

  public WorkflowExecution start(EventOperation eventOperation, EventInput input) {
    return WorkflowClient.start(eventOperation::execute, input);
  }

  /**
   * Creates a typed workflow stub for the given workflow interface.
   *
   * @param workflowInterface the workflow interface class
   * @param workflowId the unique workflow ID
   * @param <T> the workflow interface type
   * @return a typed Temporal workflow stub
   */
  public <T> T newWorkflowStub(Class<T> workflowInterface, String workflowId) {
    String queue = taskQueue.isBlank() ? "cbs-nova-task-queue" : taskQueue;
    WorkflowOptions options = WorkflowOptions.newBuilder()
        .setWorkflowId(workflowId)
        .setTaskQueue(queue)
        .build();
    return workflowClient.newWorkflowStub(workflowInterface, options);
  }

  /**
   * Registers all generated workflow implementations from the registry with the given Temporal
   * worker.
   *
   * @param worker the Temporal worker to register workflows with
   */
  @SuppressWarnings("unchecked")
  public void registerWorkflows(Worker worker) {
    List<Class<?>> workflowClasses = new ArrayList<>();
    for (String code : artifactRegistry.getWorkflowCodes()) {
      Class<EventOperation> workflowInterface = artifactRegistry.getWorkflowInterface(code);
      EventOperation impl = artifactRegistry.getWorkflow(code, workflowInterface);
      workflowClasses.add(impl.getClass());
    }
    if (!workflowClasses.isEmpty()) {
      worker.registerWorkflowImplementationTypes(workflowClasses.toArray(new Class<?>[0]));
    }
  }
}
