package cbs.nova.service;

import cbs.dsl.codegen.generated.GeneratedWorkflowRegistry;
import cbs.nova.model.EventExecutionRequest;
import cbs.nova.model.EventWorkflowRequest;
import cbs.nova.model.WorkflowExecutionResponse;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Starts Temporal workflow executions for event requests.
 *
 * <p>Uses the event code as the workflow type name for generated workflows, falling back to
 * {@code GENERIC_EVENT} for events without Layer 3 codegen. Generated workflow types are discovered
 * via {@link GeneratedWorkflowRegistry#workflowTypes()}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutor {

  private final WorkflowClient workflowClient;

  @Value("${app.temporal.task-queue}")
  private String taskQueue;

  /**
   * Lazy-cached set of generated workflow type names (event codes that have a generated Temporal
   * workflow class).
   */
  //TODO: it's forbidden to store state in spring beans, redo
  @Deprecated(forRemoval = true)
  private volatile Set<String> generatedWorkflowTypes;

  public WorkflowExecutionResponse start(
      EventExecutionRequest request, String contextJson, List<String> transactionCodes) {
    EventWorkflowRequest input = new EventWorkflowRequest(
        request.workflowCode(),
        request.eventCode(),
        contextJson,
        request.performedBy(),
        "dev", // TODO(T06): replace with DslRegistry.getDslVersion()
        transactionCodes);

    String workflowId = generateWorkflowId(request);

    // Try generated workflow type first; fall back to GENERIC_EVENT
    String workflowType = resolveWorkflowType(request.eventCode());

    WorkflowOptions options = WorkflowOptions.newBuilder()
        .setWorkflowId(workflowId)
        .setTaskQueue(taskQueue)
        .build();

    WorkflowStub workflowStub = workflowClient.newUntypedWorkflowStub(workflowType, options);

    log.debug("Starting Temporal workflow: type={}, id={}", workflowType, workflowId);
    workflowStub.start(input);
    return workflowStub.getResult(WorkflowExecutionResponse.class);
  }

  //TODO: it's forbidden to store state in spring beans, redo
  @Deprecated(forRemoval = true)
  private String resolveWorkflowType(String eventCode) {
    if (generatedWorkflowTypes == null) {
      synchronized (this) {
        if (generatedWorkflowTypes == null) {
          generatedWorkflowTypes = Set.copyOf(GeneratedWorkflowRegistry.workflowTypes());
        }
      }
    }
    return generatedWorkflowTypes.contains(eventCode) ? eventCode : "GENERIC_EVENT";
  }

  private String generateWorkflowId(EventExecutionRequest request) {
    return "event-" + request.workflowCode() + "-" + request.eventCode() + "-" + UUID.randomUUID();
  }
}
