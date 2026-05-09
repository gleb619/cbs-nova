package cbs.app.temporal.workflow;

import cbs.nova.model.EventWorkflowRequest;
import cbs.nova.model.WorkflowExecutionResponse;
import cbs.nova.registry.DslRegistry;
import cbs.nova.repository.EventExecutionRepository;
import cbs.nova.repository.WorkflowExecutionRepository;
import cbs.nova.repository.WorkflowTransitionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
// TODO: remove
@Deprecated(forRemoval = true)
public class GenericEventWorkflowImpl implements EventWorkflow {

  private final DslRegistry dslRegistry;
  private final WorkflowExecutionRepository workflowExecutionRepository;
  private final EventExecutionRepository eventExecutionRepository;
  private final WorkflowTransitionLogRepository transitionLogRepository;

  @Override
  public WorkflowExecutionResponse execute(EventWorkflowRequest request) {
    log.info("Executing generic event workflow for event: {}", request.eventCode());

    // TODO: implement generic fallback logic via EventRunner or inline execution
    return new WorkflowExecutionResponse(null, "COMPLETED");
  }
}
