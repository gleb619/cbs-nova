package cbs.app.temporal.workflow;

import cbs.app.temporal.activity.TransactionActivity;
import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.TransitionRule;
import cbs.dsl.api.WorkflowDefinition;
import cbs.dsl.runtime.DslRegistry;
import cbs.nova.entity.EventExecutionEntity;
import cbs.nova.entity.EventStatus;
import cbs.nova.entity.WorkflowExecutionEntity;
import cbs.nova.entity.WorkflowStatus;
import cbs.nova.entity.WorkflowTransitionLogEntity;
import cbs.nova.model.EventWorkflowRequest;
import cbs.nova.model.WorkflowExecutionResponse;
import cbs.nova.repository.EventExecutionRepository;
import cbs.nova.repository.WorkflowExecutionRepository;
import cbs.nova.repository.WorkflowTransitionLogRepository;
import cbs.nova.service.EventWorkflow;
import cbs.temporal.TemporalTransactionsScope;
import io.temporal.activity.ActivityOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.OffsetDateTime;

@Slf4j
@RequiredArgsConstructor
public class EventWorkflowImpl implements EventWorkflow {

  // TODO(T11): move to activity for replay safety
  private final DslRegistry dslRegistry;
  private final WorkflowExecutionRepository workflowExecutionRepository;
  private final EventExecutionRepository eventExecutionRepository;
  private final WorkflowTransitionLogRepository transitionLogRepository;

  @Override
  public WorkflowExecutionResponse execute(EventWorkflowRequest input) {
    log.debug(
        "Starting workflow execution: workflowCode={}, eventCode={}, performedBy={}",
        input.workflowCode(),
        input.eventCode(),
        input.performedBy());

    // 1. Look up workflow definition
    WorkflowDefinition workflowDefinition = dslRegistry.getWorkflows().get(input.workflowCode());
    if (workflowDefinition == null) {
      throw ApplicationFailure.newFailure(
          "Workflow not found: " + input.workflowCode(), "NOT_FOUND");
    }
    log.debug("Found workflow definition: {}", workflowDefinition.getCode());

    // 2. Persist workflow execution entity
    WorkflowExecutionEntity workflowExecution = createWorkflowExecution(input, workflowDefinition);
    log.debug("Created workflow execution entity: id={}", workflowExecution.getId());

    // 3. Find matching transition rule
    TransitionRule transitionRule = findTransitionRule(workflowDefinition, input.eventCode());

    // 4. Persist event execution entity
    String temporalWorkflowId = Workflow.getInfo().getWorkflowId();
    EventExecutionEntity eventExecution =
        createEventExecution(input, workflowExecution, transitionRule, temporalWorkflowId);
    log.debug("Created event execution entity: id={}", eventExecution.getId());

    // 5. Create activity stub and execute transactions
    TransactionActivity activityStub = Workflow.newActivityStub(
        TransactionActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .build());

    TransactionExecutionResult txResult = executeTransactions(
        transitionRule.getEvent(), input, workflowExecution.getId(), activityStub);

    // 6. Update entities based on outcome
    if (txResult.allSucceeded()) {
      handleSuccessOutcome(
          workflowExecution, eventExecution, transitionRule, workflowDefinition, input);
      log.debug(
          "Workflow completed successfully: workflowId={}, newState={}",
          workflowExecution.getId(),
          transitionRule.getTo());
    } else {
      handleFaultOutcome(
          workflowExecution, eventExecution, transitionRule, txResult.errorMessage(), input);
      log.debug(
          "Workflow faulted: workflowId={}, faultState={}, message={}",
          workflowExecution.getId(),
          transitionRule.getOnFault(),
          txResult.errorMessage());
    }

    WorkflowExecutionResponse result = new WorkflowExecutionResponse(
        workflowExecution.getId(), workflowExecution.getStatus().name());
    log.debug(
        "Workflow execution finished: workflowId={}, status={}",
        workflowExecution.getId(),
        result.status());
    return result;
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

  private WorkflowExecutionEntity createWorkflowExecution(
      EventWorkflowRequest input, WorkflowDefinition workflowDefinition) {
    WorkflowExecutionEntity entity = new WorkflowExecutionEntity();
    entity.setWorkflowCode(input.workflowCode());
    entity.setDslVersion(input.dslVersion());
    entity.setCurrentState(workflowDefinition.getInitial());
    entity.setStatus(WorkflowStatus.ACTIVE);
    entity.setContext(input.contextJson());
    entity.setDisplayData("{}");
    entity.setPerformedBy(input.performedBy());
    entity.setCreatedAt(OffsetDateTime.now());
    entity.setUpdatedAt(OffsetDateTime.now());
    return workflowExecutionRepository.save(entity);
  }

  private EventExecutionEntity createEventExecution(
      EventWorkflowRequest input,
      WorkflowExecutionEntity workflowExecution,
      TransitionRule transitionRule,
      String temporalWorkflowId) {
    EventExecutionEntity entity = new EventExecutionEntity();
    entity.setEventCode(input.eventCode());
    entity.setDslVersion(input.dslVersion());
    entity.setAction(transitionRule.getOn().name());
    entity.setStatus(EventStatus.RUNNING);
    entity.setContext(input.contextJson());
    entity.setExecutedTransactions("[]");
    entity.setTemporalWorkflowId(temporalWorkflowId);
    entity.setWorkflowExecutionId(workflowExecution.getId());
    entity.setPerformedBy(input.performedBy());
    entity.setCreatedAt(OffsetDateTime.now());
    entity.setUpdatedAt(OffsetDateTime.now());
    return eventExecutionRepository.save(entity);
  }

  private TransactionExecutionResult executeTransactions(
      EventDefinition eventDefinition,
      EventWorkflowRequest input,
      Long workflowExecutionId,
      TransactionActivity activityStub) {
    if (eventDefinition.getTransactionsBlock() == null) {
      return new TransactionExecutionResult(true, null);
    }

    log.debug("Executing transactions block for event: {}", eventDefinition.getCode());

    TemporalTransactionsScope scope = new TemporalTransactionsScope(
        activityStub,
        workflowExecutionId,
        input.performedBy(),
        input.dslVersion(),
        input.contextJson());

    TemporalTransactionsScope.executeBlock(eventDefinition.getTransactionsBlock(), scope);

    return new TransactionExecutionResult(!scope.getFailed(), scope.getErrorMessage());
  }

  private void handleSuccessOutcome(
      WorkflowExecutionEntity workflowExecution,
      EventExecutionEntity eventExecution,
      TransitionRule transitionRule,
      WorkflowDefinition workflowDefinition,
      EventWorkflowRequest input) {
    eventExecution.setStatus(EventStatus.COMPLETED);
    eventExecution.setCompletedAt(OffsetDateTime.now());
    eventExecution.setUpdatedAt(OffsetDateTime.now());
    eventExecutionRepository.save(eventExecution);

    String toState = transitionRule.getTo();
    boolean isTerminal = workflowDefinition.getTerminalStates().contains(toState);
    String fromState = workflowExecution.getCurrentState();
    workflowExecution.setCurrentState(toState);
    workflowExecution.setStatus(isTerminal ? WorkflowStatus.CLOSED : WorkflowStatus.ACTIVE);
    workflowExecution.setUpdatedAt(OffsetDateTime.now());
    workflowExecutionRepository.save(workflowExecution);

    createTransitionLog(
        workflowExecution,
        eventExecution,
        transitionRule,
        fromState,
        toState,
        "COMPLETED",
        null,
        input);
  }

  private void handleFaultOutcome(
      WorkflowExecutionEntity workflowExecution,
      EventExecutionEntity eventExecution,
      TransitionRule transitionRule,
      String faultMessage,
      EventWorkflowRequest input) {
    eventExecution.setStatus(EventStatus.FAULTED);
    eventExecution.setUpdatedAt(OffsetDateTime.now());
    eventExecutionRepository.save(eventExecution);

    String fromState = workflowExecution.getCurrentState();
    workflowExecution.setCurrentState(transitionRule.getOnFault());
    workflowExecution.setStatus(WorkflowStatus.FAULTED);
    workflowExecution.setUpdatedAt(OffsetDateTime.now());
    workflowExecutionRepository.save(workflowExecution);

    createTransitionLog(
        workflowExecution,
        eventExecution,
        transitionRule,
        fromState,
        transitionRule.getOnFault(),
        "FAULTED",
        faultMessage,
        input);
  }

  private void createTransitionLog(
      WorkflowExecutionEntity workflowExecution,
      EventExecutionEntity eventExecution,
      TransitionRule transitionRule,
      String fromState,
      String toState,
      String status,
      String faultMessage,
      EventWorkflowRequest input) {
    WorkflowTransitionLogEntity entity = new WorkflowTransitionLogEntity();
    entity.setWorkflowExecutionId(workflowExecution.getId());
    entity.setEventExecutionId(eventExecution.getId());
    entity.setAction(transitionRule.getOn().name());
    entity.setFromState(fromState);
    entity.setToState(toState);
    entity.setStatus(status);
    entity.setFaultMessage(faultMessage);
    entity.setDslVersion(input.dslVersion());
    entity.setPerformedBy(input.performedBy());
    entity.setCreatedAt(OffsetDateTime.now());
    transitionLogRepository.save(entity);
  }

  private record TransactionExecutionResult(boolean allSucceeded, String errorMessage) {}
}
