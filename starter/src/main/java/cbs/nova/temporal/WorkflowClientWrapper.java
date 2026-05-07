package cbs.nova.temporal;

import cbs.nova.temporal.registry.WorkflowConfig;
import cbs.nova.temporal.registry.WorkflowRegistry;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;

/**
 * Spring bean wrapper around {@link WorkflowClient} that hides Temporal boilerplate and resolves
 * workflow configuration from a {@link WorkflowRegistry}.
 *
 * <p>Typical usage from a Spring service (outside a workflow):
 *
 * <pre>
 *   OrderWorkflow stub = workflowClientWrapper.newWorkflowStub(
 *       OrderWorkflow.class, "OrderWorkflow", "order-123");
 *   stub.execute(request);
 * </pre>
 *
 * <p>For asynchronous starts:
 *
 * <pre>
 *   WorkflowExecution exec = workflowClientWrapper.startWorkflowAsync(
 *       OrderWorkflow.class, "OrderWorkflow", "order-123", request);
 * </pre>
 */
@RequiredArgsConstructor
public class WorkflowClientWrapper {

  private final WorkflowClient workflowClient;
  private final WorkflowRegistry workflowRegistry;

  /**
   * Creates a typed workflow stub, looking up configuration from the {@link WorkflowRegistry}.
   *
   * <p>The returned stub can be used for synchronous execution (calling the {@code @WorkflowMethod}
   * directly) or passed to {@link #startWorkflowAsync(Object...)}.
   *
   * @param <T> the workflow interface type
   * @param workflowInterface the workflow interface class
   * @param logicalWorkflowName the logical name used for registry lookup
   * @param workflowId the unique workflow ID
   * @return a typed workflow stub ready for execution
   * @throws IllegalArgumentException if the logical name is not registered
   */
  public <T> T newWorkflowStub(
      Class<T> workflowInterface, String logicalWorkflowName, String workflowId) {

    WorkflowConfig config = workflowRegistry.getWorkflowConfig(logicalWorkflowName);

    WorkflowOptions.Builder optionsBuilder =
        WorkflowOptions.newBuilder().setWorkflowId(workflowId).setTaskQueue(config.taskQueue());

    if (config.workflowExecutionTimeout() != null) {
      optionsBuilder.setWorkflowExecutionTimeout(config.workflowExecutionTimeout());
    }
    if (config.workflowRunTimeout() != null) {
      optionsBuilder.setWorkflowRunTimeout(config.workflowRunTimeout());
    }

    WorkflowOptions options = optionsBuilder.build();
    return workflowClient.newWorkflowStub(workflowInterface, options);
  }

  /**
   * Creates a typed workflow stub and starts it asynchronously.
   *
   * <p>This delegates to {@link WorkflowClient#start(Object, Object...)}. The workflow method
   * arguments are forwarded to the stub proxy.
   *
   * @param <T> the workflow interface type
   * @param workflowInterface the workflow interface class
   * @param logicalWorkflowName the logical name used for registry lookup
   * @param workflowId the unique workflow ID
   * @param args arguments for the workflow method
   * @return a {@link WorkflowExecution} handle containing the started workflow ID and run ID
   */
  public <T> WorkflowExecution startWorkflowAsync(
      Class<T> workflowInterface, String logicalWorkflowName, String workflowId, Object... args) {

    T stub = newWorkflowStub(workflowInterface, logicalWorkflowName, workflowId);
    return WorkflowClient.start(stub, args);
  }
}
