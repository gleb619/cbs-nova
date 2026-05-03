package cbs.app.temporal.workflow;

import cbs.app.temporal.activity.TransactionActivity;
import cbs.app.temporal.activity.TransactionActivityInput;
import cbs.app.temporal.activity.TransactionResult;
import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.TransitionRule;
import cbs.dsl.api.WorkflowDefinition;
import cbs.dsl.runtime.DslRegistry;
import cbs.nova.entity.EventExecutionEntity;
import cbs.nova.entity.EventStatus;
import cbs.nova.entity.WorkflowExecutionEntity;
import cbs.nova.entity.WorkflowStatus;
import cbs.nova.entity.WorkflowTransitionLogEntity;
import cbs.nova.repository.EventExecutionRepository;
import cbs.nova.repository.WorkflowExecutionRepository;
import cbs.nova.repository.WorkflowTransitionLogRepository;
import cbs.nova.temporal.workflow.EventWorkflow;
import cbs.nova.temporal.workflow.EventWorkflowInput;
import cbs.nova.temporal.workflow.WorkflowExecutionResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.temporal.activity.ActivityOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

public class EventWorkflowImpl implements EventWorkflow {

  // TODO(T11): move to activity for replay safety
  private final DslRegistry dslRegistry;
  private final WorkflowExecutionRepository workflowExecutionRepository;
  private final EventExecutionRepository eventExecutionRepository;
  private final WorkflowTransitionLogRepository transitionLogRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  public EventWorkflowImpl(
      DslRegistry dslRegistry,
      WorkflowExecutionRepository workflowExecutionRepository,
      EventExecutionRepository eventExecutionRepository,
      WorkflowTransitionLogRepository transitionLogRepository) {
    this.dslRegistry = dslRegistry;
    this.workflowExecutionRepository = workflowExecutionRepository;
    this.eventExecutionRepository = eventExecutionRepository;
    this.transitionLogRepository = transitionLogRepository;
  }

  @Override
  public WorkflowExecutionResult execute(EventWorkflowInput input) {
    // 1. Look up workflow definition
    WorkflowDefinition workflowDefinition = dslRegistry.getWorkflows().get(input.workflowCode());
    if (workflowDefinition == null) {
      throw ApplicationFailure.newFailure(
          "Workflow not found: " + input.workflowCode(), "NOT_FOUND");
    }

    // 2. Persist workflow execution entity (ACTIVE, initial state)
    WorkflowExecutionEntity workflowExecution = new WorkflowExecutionEntity();
    workflowExecution.setWorkflowCode(input.workflowCode());
    workflowExecution.setDslVersion(input.dslVersion());
    workflowExecution.setCurrentState(workflowDefinition.getInitial());
    workflowExecution.setStatus(WorkflowStatus.ACTIVE);
    workflowExecution.setContext(input.contextJson());
    workflowExecution.setDisplayData("{}");
    workflowExecution.setPerformedBy(input.performedBy());
    workflowExecution.setCreatedAt(OffsetDateTime.now());
    workflowExecution.setUpdatedAt(OffsetDateTime.now());
    workflowExecutionRepository.save(workflowExecution);

    // 3. Find matching transition rule
    TransitionRule transitionRule = findTransitionRule(workflowDefinition, input.eventCode());

    // 4. Look up event definition
    EventDefinition eventDefinition = transitionRule.getEvent();

    // 5. Persist event execution entity (RUNNING)
    EventExecutionEntity eventExecution = new EventExecutionEntity();
    eventExecution.setEventCode(input.eventCode());
    eventExecution.setDslVersion(input.dslVersion());
    eventExecution.setAction(transitionRule.getOn().name());
    eventExecution.setStatus(EventStatus.RUNNING);
    eventExecution.setContext(input.contextJson());
    eventExecution.setExecutedTransactions("[]");
    String temporalWorkflowId = Workflow.getInfo().getWorkflowId();
    eventExecution.setTemporalWorkflowId(temporalWorkflowId);
    eventExecution.setWorkflowExecution(workflowExecution);
    eventExecution.setPerformedBy(input.performedBy());
    eventExecution.setCreatedAt(OffsetDateTime.now());
    eventExecution.setUpdatedAt(OffsetDateTime.now());
    eventExecutionRepository.save(eventExecution);

    // 6. Create activity stub and execute transactions
    TransactionActivity activityStub = Workflow.newActivityStub(
        TransactionActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .build());

    boolean allTransactionsSucceeded = true;
    String faultMessage = null;

    for (TransactionDefinition txDef : eventDefinition.getTransactionsBlock()) {
      TransactionActivityInput txInput = new TransactionActivityInput(
          txDef.getCode(),
          input.contextJson(),
          workflowExecution.getId(),
          input.performedBy(),
          input.dslVersion());

      TransactionResult txResult = activityStub.executeTransaction(txInput);

      if (!txResult.success()) {
        allTransactionsSucceeded = false;
        faultMessage = txResult.errorMessage();
        break;
      }
    }

    // 7. Update entities based on outcome
    if (allTransactionsSucceeded) {
      // Success path
      eventExecution.setStatus(EventStatus.COMPLETED);
      eventExecution.setCompletedAt(OffsetDateTime.now());
      eventExecution.setUpdatedAt(OffsetDateTime.now());
      eventExecutionRepository.save(eventExecution);

      String toState = transitionRule.getTo();
      boolean isTerminal = workflowDefinition.getTerminalStates().contains(toState);
      workflowExecution.setCurrentState(toState);
      workflowExecution.setStatus(isTerminal ? WorkflowStatus.CLOSED : WorkflowStatus.ACTIVE);
      workflowExecution.setUpdatedAt(OffsetDateTime.now());
      workflowExecutionRepository.save(workflowExecution);

      WorkflowTransitionLogEntity transitionLog = new WorkflowTransitionLogEntity();
      transitionLog.setWorkflowExecution(workflowExecution);
      transitionLog.setEventExecution(eventExecution);
      transitionLog.setAction(transitionRule.getOn().name());
      transitionLog.setFromState(workflowDefinition.getInitial());
      transitionLog.setToState(toState);
      transitionLog.setStatus("COMPLETED");
      transitionLog.setDslVersion(input.dslVersion());
      transitionLog.setPerformedBy(input.performedBy());
      transitionLog.setCreatedAt(OffsetDateTime.now());
      transitionLogRepository.save(transitionLog);
    } else {
      // Fault path
      eventExecution.setStatus(EventStatus.FAULTED);
      eventExecution.setUpdatedAt(OffsetDateTime.now());
      eventExecutionRepository.save(eventExecution);

      workflowExecution.setCurrentState(transitionRule.getOnFault());
      workflowExecution.setStatus(WorkflowStatus.FAULTED);
      workflowExecution.setUpdatedAt(OffsetDateTime.now());
      workflowExecutionRepository.save(workflowExecution);

      WorkflowTransitionLogEntity transitionLog = new WorkflowTransitionLogEntity();
      transitionLog.setWorkflowExecution(workflowExecution);
      transitionLog.setEventExecution(eventExecution);
      transitionLog.setAction(transitionRule.getOn().name());
      transitionLog.setFromState(workflowDefinition.getInitial());
      transitionLog.setToState(transitionRule.getOnFault());
      transitionLog.setStatus("FAULTED");
      transitionLog.setFaultMessage(faultMessage);
      transitionLog.setDslVersion(input.dslVersion());
      transitionLog.setPerformedBy(input.performedBy());
      transitionLog.setCreatedAt(OffsetDateTime.now());
      transitionLogRepository.save(transitionLog);
    }

    return new WorkflowExecutionResult(
        workflowExecution.getId(), workflowExecution.getStatus().name());
  }

  private TransitionRule findTransitionRule(
      WorkflowDefinition workflowDefinition, String eventCode) {
    String currentState = workflowDefinition.getInitial();
    for (TransitionRule rule : workflowDefinition.getTransitions()) {
      if (rule.getFrom().equals(currentState) && rule.getEvent().getCode().equals(eventCode)) {
        return rule;
      }
    }
    throw ApplicationFailure.newFailure(
        "No transition from '" + currentState + "' on event '" + eventCode + "'",
        "INVALID_TRANSITION");
  }

  private Map<String, Object> parseContextJson(String contextJson) {
    try {
      return objectMapper.readValue(contextJson, new TypeReference<>() {
      });
    } catch (JsonProcessingException e) {
      return Map.of();
    }
  }
}
