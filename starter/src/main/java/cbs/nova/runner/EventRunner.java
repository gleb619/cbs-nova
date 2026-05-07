package cbs.nova.runner;

import cbs.dsl.api.EventDefinition;
import cbs.nova.model.EventExecutionRequest;
import cbs.nova.model.EventExecutionResponse;
import cbs.nova.registry.DslRegistry;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventRunner {

  private final WorkflowClient workflowClient;
  private final DslRegistry dslRegistry;

  @Value("${app.temporal.task-queue}")
  private String taskQueue;

  /**
   * Runs an event by starting its generated Temporal workflow.
   *
   * @param request the execution request containing eventCode, parameters, performer
   * @return the execution response with executionId and status
   */
  public EventExecutionResponse run(EventExecutionRequest request) {
    log.debug("Running event: code={}, performedBy={}", request.eventCode(), request.performedBy());

    EventDefinition eventDef = dslRegistry.resolveEvent(request.eventCode());

    String workflowId = generateWorkflowId(request);
    String workflowType = request.eventCode();

    WorkflowOptions options = WorkflowOptions.newBuilder()
        .setWorkflowId(workflowId)
        .setTaskQueue(taskQueue)
        .build();

    // Use untyped stub so we don't need the workflow interface class at compile time
    WorkflowStub workflowStub = workflowClient.newUntypedWorkflowStub(workflowType, options);

    log.debug("Starting Temporal workflow: type={}, id={}", workflowType, workflowId);
    workflowStub.start(request);

    // For now return async; in future we may wait for result
    return new EventExecutionResponse(null, "STARTED");
  }

  private String generateWorkflowId(EventExecutionRequest request) {
    return "event-" + request.workflowCode() + "-" + request.eventCode() + "-" + UUID.randomUUID();
  }
}
