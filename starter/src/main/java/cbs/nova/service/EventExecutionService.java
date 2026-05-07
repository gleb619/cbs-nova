package cbs.nova.service;

import cbs.dsl.api.WorkflowDefinition;
import cbs.nova.entity.EventExecutionEntity;
import cbs.nova.entity.EventStatus;
import cbs.nova.entity.WorkflowExecutionEntity;
import cbs.nova.entity.WorkflowStatus;
import cbs.nova.entity.WorkflowTransitionLogEntity;
import cbs.nova.model.EventExecutionRequest;
import cbs.nova.model.EventExecutionResponse;
import cbs.nova.registry.DslRegistry;
import cbs.nova.repository.EventExecutionRepository;
import cbs.nova.repository.WorkflowExecutionRepository;
import cbs.nova.repository.WorkflowTransitionLogRepository;
import cbs.nova.runner.EventRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventExecutionService {

  private final WorkflowResolver workflowResolver;
  private final EventRunner eventRunner;
  private final ContextEncryptionService contextEncryptionService;
  private final WorkflowExecutionRepository workflowExecutionRepository;
  private final EventExecutionRepository eventExecutionRepository;
  private final WorkflowTransitionLogRepository workflowTransitionLogRepository;
  private final DslRegistry dslRegistry;

  public EventExecutionResponse execute(EventExecutionRequest request) {
    log.debug(
        "Executing event: workflow={}, event={}", request.workflowCode(), request.eventCode());

    WorkflowDefinition workflowDefinition = workflowResolver.resolve(request.workflowCode());
    log.debug("Resolved workflow: {}", workflowDefinition.getCode());

    Map<String, Object> parameters = request.parameters() != null ? request.parameters() : Map.of();
    String encryptedContext = contextEncryptionService.encrypt(parameters);

    WorkflowExecutionEntity workflowExecution = WorkflowExecutionEntity.builder()
        .workflowCode(request.workflowCode())
        .dslVersion("dev")
        .currentState(workflowDefinition.getInitial())
        .status(WorkflowStatus.ACTIVE)
        .context(encryptedContext)
        .displayData("{}")
        .performedBy(request.performedBy())
        .createdAt(OffsetDateTime.now())
        .updatedAt(OffsetDateTime.now())
        .build();
    workflowExecution = workflowExecutionRepository.save(workflowExecution);

    EventExecutionEntity eventExecution = EventExecutionEntity.builder()
        .eventCode(request.eventCode())
        .dslVersion("dev")
        .action(request.eventCode())
        .status(EventStatus.RUNNING)
        .context(encryptedContext)
        .executedTransactions("[]")
        .workflowExecutionId(workflowExecution.getId())
        .performedBy(request.performedBy())
        .createdAt(OffsetDateTime.now())
        .updatedAt(OffsetDateTime.now())
        .build();
    eventExecution = eventExecutionRepository.save(eventExecution);

    WorkflowTransitionLogEntity transitionLog = WorkflowTransitionLogEntity.builder()
        .workflowExecutionId(workflowExecution.getId())
        .eventExecutionId(eventExecution.getId())
        .action(request.eventCode())
        .fromState("")
        .toState(workflowDefinition.getInitial())
        .status("STARTED")
        .dslVersion("dev")
        .performedBy(request.performedBy())
        .createdAt(OffsetDateTime.now())
        .build();
    workflowTransitionLogRepository.save(transitionLog);

    EventExecutionResponse runnerResponse = eventRunner.run(request);
    return new EventExecutionResponse(workflowExecution.getId(), runnerResponse.status());
  }
}
