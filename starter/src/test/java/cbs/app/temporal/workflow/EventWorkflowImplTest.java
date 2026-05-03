package cbs.app.temporal.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.app.temporal.activity.TransactionActivityImpl;
import cbs.dsl.api.Action;
import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.TransitionRule;
import cbs.dsl.api.WorkflowDefinition;
import cbs.dsl.api.context.TransactionContext;
import cbs.dsl.runtime.DslRegistry;
import cbs.nova.entity.EventExecutionEntity;
import cbs.nova.entity.WorkflowExecutionEntity;
import cbs.nova.entity.WorkflowTransitionLogEntity;
import cbs.nova.model.EventWorkflowRequest;
import cbs.nova.model.WorkflowExecutionResponse;
import cbs.nova.repository.EventExecutionRepository;
import cbs.nova.repository.WorkflowExecutionRepository;
import cbs.nova.repository.WorkflowTransitionLogRepository;
import cbs.nova.service.EventWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import kotlin.Unit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

class EventWorkflowImplTest {

  private TestWorkflowEnvironment testEnv;
  private Worker worker;
  private WorkflowClient client;

  private DslRegistry dslRegistry;
  private WorkflowExecutionRepository workflowExecutionRepository;
  private EventExecutionRepository eventExecutionRepository;
  private WorkflowTransitionLogRepository transitionLogRepository;

  @BeforeEach
  void setUp() {
    testEnv = TestWorkflowEnvironment.newInstance();
    worker = testEnv.newWorker("TEST_QUEUE");

    dslRegistry = mock(DslRegistry.class);
    workflowExecutionRepository = mock(WorkflowExecutionRepository.class);
    eventExecutionRepository = mock(EventExecutionRepository.class);
    transitionLogRepository = mock(WorkflowTransitionLogRepository.class);

    when(workflowExecutionRepository.save(any(WorkflowExecutionEntity.class)))
        .thenAnswer(invocation -> {
          WorkflowExecutionEntity entity = invocation.getArgument(0);
          entity.setId(1L);
          return entity;
        });

    when(eventExecutionRepository.save(any(EventExecutionEntity.class))).thenAnswer(invocation -> {
      EventExecutionEntity entity = invocation.getArgument(0);
      entity.setId(1L);
      return entity;
    });

    when(transitionLogRepository.save(any(WorkflowTransitionLogEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    worker.registerWorkflowImplementationFactory(
        EventWorkflow.class,
        () -> new EventWorkflowImpl(
            dslRegistry,
            workflowExecutionRepository,
            eventExecutionRepository,
            transitionLogRepository));
    worker.registerActivitiesImplementations(
        new TransactionActivityImpl(dslRegistry, new ObjectMapper()));
    testEnv.start();
    client = testEnv.getWorkflowClient();
  }

  @AfterEach
  void tearDown() {
    testEnv.close();
  }

  @Test
  @DisplayName(
      "Should complete workflow and transition to CLOSED when terminal state and single transaction succeeds")
  void shouldCompleteWorkflowAndTransitionToClosedWhenTerminalStateAndSingleTransactionSucceeds() {
    // Arrange
    TransactionDefinition txDef = mock(TransactionDefinition.class);
    when(txDef.getCode()).thenReturn("SUCCESS_TX");
    when(dslRegistry.getTransactions()).thenReturn(Map.of("SUCCESS_TX", txDef));

    EventDefinition eventDef = mock(EventDefinition.class);
    when(eventDef.getCode()).thenReturn("TEST_EVENT");
    when(eventDef.getContextBlock()).thenReturn(ctx -> Unit.INSTANCE);
    when(eventDef.getDisplayBlock()).thenReturn(scope -> Unit.INSTANCE);
    when(eventDef.getTransactionsBlock()).thenReturn(TestTransactionBlocks.singleStepAwait(txDef));
    when(eventDef.getFinishBlock()).thenReturn((ctx, ex) -> Unit.INSTANCE);

    TransitionRule rule =
        new TransitionRule("INIT", "DONE", Action.CLOSE, eventDef, "FAULTED", null);

    WorkflowDefinition wfDef = mock(WorkflowDefinition.class);
    when(wfDef.getCode()).thenReturn("TEST_WF");
    when(wfDef.getInitial()).thenReturn("INIT");
    when(wfDef.getTerminalStates()).thenReturn(List.of("DONE"));
    when(wfDef.getStates()).thenReturn(List.of("INIT", "DONE"));
    when(wfDef.getTransitions()).thenReturn(List.of(rule));

    when(dslRegistry.getWorkflows()).thenReturn(Map.of("TEST_WF", wfDef));

    EventWorkflow workflow = client.newWorkflowStub(
        EventWorkflow.class,
        WorkflowOptions.newBuilder().setTaskQueue("TEST_QUEUE").build());

    // Act
    EventWorkflowRequest input =
        new EventWorkflowRequest("TEST_WF", "TEST_EVENT", "{}", "testUser", "1.0.0");
    WorkflowExecutionResponse result = workflow.execute(input);

    // Assert
    assertEquals("CLOSED", result.status());
  }

  @Test
  @DisplayName("Should transition to FAULTED when transaction fails")
  void shouldTransitionToFaultedWhenTransactionFails() {
    // Arrange
    TransactionDefinition failingTx = mock(TransactionDefinition.class);
    when(failingTx.getCode()).thenReturn("FAIL_TX");
    when(dslRegistry.getTransactions()).thenReturn(Map.of("FAIL_TX", failingTx));
    doThrow(new RuntimeException("Transaction failed"))
        .when(failingTx)
        .execute(any(TransactionContext.class));
    doThrow(new RuntimeException("Rollback also fails"))
        .when(failingTx)
        .rollback(any(TransactionContext.class));

    EventDefinition eventDef = mock(EventDefinition.class);
    when(eventDef.getCode()).thenReturn("TEST_EVENT");
    when(eventDef.getContextBlock()).thenReturn(ctx -> Unit.INSTANCE);
    when(eventDef.getDisplayBlock()).thenReturn(scope -> Unit.INSTANCE);
    when(eventDef.getTransactionsBlock())
        .thenReturn(TestTransactionBlocks.singleStepAwait(failingTx));
    when(eventDef.getFinishBlock()).thenReturn((ctx, ex) -> Unit.INSTANCE);

    TransitionRule rule =
        new TransitionRule("INIT", "DONE", Action.CLOSE, eventDef, "FAULTED", null);

    WorkflowDefinition wfDef = mock(WorkflowDefinition.class);
    when(wfDef.getCode()).thenReturn("TEST_WF");
    when(wfDef.getInitial()).thenReturn("INIT");
    when(wfDef.getTerminalStates()).thenReturn(List.of("DONE"));
    when(wfDef.getStates()).thenReturn(List.of("INIT", "DONE"));
    when(wfDef.getTransitions()).thenReturn(List.of(rule));

    when(dslRegistry.getWorkflows()).thenReturn(Map.of("TEST_WF", wfDef));

    EventWorkflow workflow = client.newWorkflowStub(
        EventWorkflow.class,
        WorkflowOptions.newBuilder().setTaskQueue("TEST_QUEUE").build());

    // Act
    EventWorkflowRequest input =
        new EventWorkflowRequest("TEST_WF", "TEST_EVENT", "{}", "testUser", "1.0.0");
    WorkflowExecutionResponse result = workflow.execute(input);

    // Assert
    assertEquals("FAULTED", result.status());
  }

  @Test
  @DisplayName("Should throw ApplicationFailure when workflow code is unknown")
  void shouldThrowApplicationFailureWhenWorkflowCodeIsUnknown() {
    // Arrange
    when(dslRegistry.getWorkflows()).thenReturn(Map.of());

    EventWorkflow workflow = client.newWorkflowStub(
        EventWorkflow.class,
        WorkflowOptions.newBuilder().setTaskQueue("TEST_QUEUE").build());

    // Act & Assert
    EventWorkflowRequest input =
        new EventWorkflowRequest("UNKNOWN_WF", "TEST_EVENT", "{}", "testUser", "1.0.0");
    assertThrows(WorkflowFailedException.class, () -> workflow.execute(input));
  }

  @Test
  @DisplayName("Should throw ApplicationFailure when no matching transition exists")
  void shouldThrowApplicationFailureWhenNoMatchingTransitionExists() {
    // Arrange
    WorkflowDefinition wfDef = mock(WorkflowDefinition.class);
    when(wfDef.getCode()).thenReturn("TEST_WF");
    when(wfDef.getInitial()).thenReturn("INIT");
    when(wfDef.getTerminalStates()).thenReturn(List.of("DONE"));
    when(wfDef.getStates()).thenReturn(List.of("INIT", "DONE"));
    when(wfDef.getTransitions()).thenReturn(List.of());

    when(dslRegistry.getWorkflows()).thenReturn(Map.of("TEST_WF", wfDef));

    EventWorkflow workflow = client.newWorkflowStub(
        EventWorkflow.class,
        WorkflowOptions.newBuilder().setTaskQueue("TEST_QUEUE").build());

    // Act & Assert
    EventWorkflowRequest input =
        new EventWorkflowRequest("TEST_WF", "UNKNOWN_EVENT", "{}", "testUser", "1.0.0");
    assertThrows(WorkflowFailedException.class, () -> workflow.execute(input));
  }
}
