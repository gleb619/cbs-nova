package cbs.nova.service;

import cbs.dsl.runtime.DslRegistry;
import cbs.nova.model.EventExecutionRequest;
import cbs.nova.temporal.workflow.EventWorkflow;
import cbs.nova.temporal.workflow.EventWorkflowInput;
import cbs.nova.temporal.workflow.WorkflowExecutionResult;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutor {

  private final WorkflowClient workflowClient;
  private final DslRegistry dslRegistry;

  @Value("${temporal.task-queue:WORKFLOW_TASK_QUEUE}")
  private String taskQueue;

  public WorkflowExecutionResult start(EventExecutionRequest request, String contextJson) {
    EventWorkflowInput input = new EventWorkflowInput(
        request.workflowCode(),
        request.eventCode(),
        contextJson,
        request.performedBy(),
        "dev"); // TODO(T06): replace with DslRegistry.getDslVersion()

    String workflowId = generateWorkflowId(request);

    WorkflowOptions options = WorkflowOptions.newBuilder()
        .setWorkflowId(workflowId)
        .setTaskQueue(taskQueue)
        .build();

    EventWorkflow workflowStub = workflowClient.newWorkflowStub(EventWorkflow.class, options);

    log.debug("Starting Temporal workflow: {}", workflowId);
    return workflowStub.execute(input);
  }

  private String generateWorkflowId(EventExecutionRequest request) {
    return "event-" + request.workflowCode() + "-" + request.eventCode() + "-" + UUID.randomUUID();
  }
}
