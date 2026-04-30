package cbs.nova.service;

import cbs.app.temporal.activity.TransactionActivity;
import cbs.app.temporal.activity.TransactionActivityInput;
import cbs.app.temporal.activity.TransactionResult;
import cbs.dsl.api.TransitionRuleDefinition;
import cbs.dsl.api.WorkflowDefinition;
import cbs.nova.entity.EventExecutionEntity;
import cbs.nova.entity.EventStatus;
import cbs.nova.entity.WorkflowExecutionEntity;
import cbs.nova.entity.WorkflowStatus;
import cbs.nova.entity.WorkflowTransitionLogEntity;
import cbs.nova.model.EventWorkflowRequest;
import cbs.nova.model.WorkflowExecutionResponse;
import cbs.nova.registry.DslRegistry;
import cbs.nova.repository.EventExecutionRepository;
import cbs.nova.repository.WorkflowExecutionRepository;
import cbs.nova.repository.WorkflowTransitionLogRepository;
import io.temporal.activity.ActivityOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Orchestrates event workflow execution including state transitions, entity persistence, and
 * transaction dispatch.
 *
 * <p>This class is designed to be called from within a Temporal workflow method. It uses
 * {@link Workflow#currentTimeMillis()} for deterministic timestamps and delegates transaction
 * execution to the {@link TransactionActivity} activity stub.
 *
 * <p><strong>TODO(T11):</strong> Move DB operations (repository calls) to a dedicated activity for
 * full replay safety.
 */
@Slf4j
@RequiredArgsConstructor
public class EventWorkflowOrchestrator {

  private final DslRegistry dslRegistry;
  private final WorkflowExecutionRepository workflowExecutionRepository;
  private final EventExecutionRepository eventExecutionRepository;
  private final WorkflowTransitionLogRepository transitionLogRepository;

  /**
   * Executes the full event workflow lifecycle.
   *
   * @param input the workflow request
   * @param transactionCodes ordered list of transaction codes to execute
   * @return the workflow execution response
   */
  public WorkflowExecutionResponse execute(
      EventWorkflowRequest input, List<String> transactionCodes) {
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

    // 2. Find matching transition rule
    TransitionRuleDefinition transitionRule =
        findTransitionRule(workflowDefinition, input.eventCode());

    // 3. Persist workflow execution entity
    WorkflowExecutionEntity workflowExecution = createWorkflowExecution(input, workflowDefinition);
    log.debug("Created workflow execution entity: id={}", workflowExecution.getId());

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

    TransactionExecutionResult txResult =
        executeTransactions(transactionCodes, input, workflowExecution.getId(), activityStub);

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

  private TransitionRuleDefinition findTransitionRule(
      WorkflowDefinition workflowDefinition, String eventCode) {
    String currentState = workflowDefinition.getInitial();
    for (TransitionRuleDefinition rule : workflowDefinition.getTransitions()) {
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
    OffsetDateTime now = now();
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    return workflowExecutionRepository.save(entity);
  }

  private EventExecutionEntity createEventExecution(
      EventWorkflowRequest input,
      WorkflowExecutionEntity workflowExecution,
      TransitionRuleDefinition transitionRule,
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
    OffsetDateTime now = now();
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    return eventExecutionRepository.save(entity);
  }

  private TransactionExecutionResult executeTransactions(
      List<String> transactionCodes,
      EventWorkflowRequest input,
      Long workflowExecutionId,
      TransactionActivity activityStub) {
    if (transactionCodes == null || transactionCodes.isEmpty()) {
      return new TransactionExecutionResult(true, null);
    }

    log.debug(
        "Executing {} transactions for event: {}", transactionCodes.size(), input.eventCode());

    for (String code : transactionCodes) {
      log.debug("Executing transaction: {}", code);
      TransactionResult result = activityStub.executeTransaction(new TransactionActivityInput(
          code, input.contextJson(), workflowExecutionId, input.performedBy(), input.dslVersion()));
      if (!result.success()) {
        return new TransactionExecutionResult(
            false, "Transaction '" + code + "' failed: " + result.errorMessage());
      }
    }

    return new TransactionExecutionResult(true, null);
  }

  private void handleSuccessOutcome(
      WorkflowExecutionEntity workflowExecution,
      EventExecutionEntity eventExecution,
      TransitionRuleDefinition transitionRule,
      WorkflowDefinition workflowDefinition,
      EventWorkflowRequest input) {
    eventExecution.setStatus(EventStatus.COMPLETED);
    eventExecution.setCompletedAt(now());
    eventExecution.setUpdatedAt(now());
    eventExecutionRepository.save(eventExecution);

    String toState = transitionRule.getTo();
    boolean isTerminal = workflowDefinition.getTerminalStates().contains(toState);
    String fromState = workflowExecution.getCurrentState();
    workflowExecution.setCurrentState(toState);
    workflowExecution.setStatus(isTerminal ? WorkflowStatus.CLOSED : WorkflowStatus.ACTIVE);
    workflowExecution.setUpdatedAt(now());
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
      TransitionRuleDefinition transitionRule,
      String faultMessage,
      EventWorkflowRequest input) {
    eventExecution.setStatus(EventStatus.FAULTED);
    eventExecution.setUpdatedAt(now());
    eventExecutionRepository.save(eventExecution);

    String fromState = workflowExecution.getCurrentState();
    workflowExecution.setCurrentState(transitionRule.getOnFault());
    workflowExecution.setStatus(WorkflowStatus.FAULTED);
    workflowExecution.setUpdatedAt(now());
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
      TransitionRuleDefinition transitionRule,
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
    entity.setCreatedAt(now());
    transitionLogRepository.save(entity);
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(Workflow.currentTimeMillis()), ZoneOffset.UTC);
  }

  private record TransactionExecutionResult(boolean allSucceeded, String errorMessage) {}
}
