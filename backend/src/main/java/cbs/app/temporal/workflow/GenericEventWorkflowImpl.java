package cbs.app.temporal.workflow;

import cbs.nova.model.EventWorkflowRequest;
import cbs.nova.model.WorkflowExecutionResponse;
import cbs.nova.registry.DslRegistry;
import cbs.nova.repository.EventExecutionRepository;
import cbs.nova.repository.WorkflowExecutionRepository;
import cbs.nova.repository.WorkflowTransitionLogRepository;
import cbs.nova.service.EventWorkflowOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class GenericEventWorkflowImpl implements EventWorkflow {

  private final DslRegistry dslRegistry;
  private final WorkflowExecutionRepository workflowExecutionRepository;
  private final EventExecutionRepository eventExecutionRepository;
  private final WorkflowTransitionLogRepository transitionLogRepository;

  @Override
  public WorkflowExecutionResponse execute(EventWorkflowRequest request) {
    log.info("Executing generic event workflow for event: {}", request.eventCode());

    EventWorkflowOrchestrator orchestrator = new EventWorkflowOrchestrator(
        dslRegistry,
        workflowExecutionRepository,
        eventExecutionRepository,
        transitionLogRepository);

    List<String> transactionCodes = request.transactionCodes();
    if (transactionCodes == null || transactionCodes.isEmpty()) {
      transactionCodes = List.of();
    }

    return orchestrator.execute(request, transactionCodes);
  }
}
