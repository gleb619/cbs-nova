package cbs.nova.service;

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
import java.util.UUID;

/**
 * Starts Temporal workflow executions for event requests.
 *
 * <p>Uses the event code as the workflow type name and dispatches to the corresponding generated
 * workflow via {@link WorkflowClient#newUntypedWorkflowStub(String, WorkflowOptions)}. The
 * generated workflows are registered by {@link cbs.app.temporal.TemporalWorkerRegistrar}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutor {

  private final WorkflowClient workflowClient;

  @Value("${app.temporal.task-queue}")
  private String taskQueue;

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

    WorkflowOptions options = WorkflowOptions.newBuilder()
        .setWorkflowId(workflowId)
        .setTaskQueue(taskQueue)
        .build();

    WorkflowStub workflowStub = workflowClient.newUntypedWorkflowStub("GENERIC_EVENT", options);

    log.debug("Starting Temporal workflow: type={}, id={}", "GENERIC_EVENT", workflowId);
    workflowStub.start(input);
    return workflowStub.getResult(WorkflowExecutionResponse.class);
  }

  private String generateWorkflowId(EventExecutionRequest request) {
    return "event-" + request.workflowCode() + "-" + request.eventCode() + "-" + UUID.randomUUID();
  }
}
