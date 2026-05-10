package cbs.nova.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.TransactionTypes.TransactionOutput;
import cbs.dsl.api.TransactionTypes.TransactionStatus;
import cbs.nova.model.TransactionExecutionRequest;
import cbs.nova.model.TransactionExecutionResponse;
import cbs.nova.registry.DefaultSpecDefinitionRegistry;
import cbs.nova.registry.DslRegistry;
import cbs.nova.temporal.WorkflowManager;
import cbs.nova.temporal.workflow.GenericTransactionRequest;
import cbs.nova.temporal.workflow.GenericWorkflow;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.workflow.Functions.Func1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@ExtendWith(MockitoExtension.class)
class TransactionRunnerTest {

  @Mock
  private WorkflowManager workflowManager;

  @Mock
  private DslRegistry dslRegistry;

  @Mock
  private DefaultSpecDefinitionRegistry specRegistry;

  @InjectMocks
  private TransactionRunner transactionRunner;

  private TransactionDefinition mockTransactionDefinition;

  @BeforeEach
  void setUp() {
    mockTransactionDefinition = mock(TransactionDefinition.class);
  }

  private static Func1<GenericTransactionRequest, TransactionOutput> anyFunc1() {
    return any(Func1.class);
  }

  @Test
  @DisplayName("shouldFilterParamsAndStartWorkflow")
  void shouldFilterParamsAndStartWorkflow() {
    TransactionExecutionRequest request = new TransactionExecutionRequest(
        "SAMPLE_TX", "admin1", Map.of("name", "Alice", "unknownParam", "value"));

    ParameterDefinition paramDef = mock(ParameterDefinition.class);
    when(paramDef.getName()).thenReturn("name");

    when(dslRegistry.resolveTransaction("SAMPLE_TX")).thenReturn(mockTransactionDefinition);
    when(mockTransactionDefinition.getParameters()).thenReturn(List.of(paramDef));
    doReturn(Object.class).when(specRegistry).getActivityInterface("SAMPLE_TX");

    GenericWorkflow mockWorkflow = mock(GenericWorkflow.class);
    when(workflowManager.newWorkflowStub(eq(GenericWorkflow.class), anyString()))
        .thenReturn(mockWorkflow);

    AtomicReference<GenericTransactionRequest> captured = new AtomicReference<>();
    try (MockedStatic<WorkflowClient> workflowClient = mockStatic(WorkflowClient.class)) {
      workflowClient
          .when(() -> WorkflowClient.start(anyFunc1(), any(GenericTransactionRequest.class)))
          .thenAnswer(invocation -> {
            captured.set(invocation.getArgument(1));
            return WorkflowExecution.newBuilder().build();
          });

      TransactionExecutionResponse response = transactionRunner.perform(request);

      assertThat(response).isNotNull();
      assertThat(response.executionId()).startsWith("transaction-SAMPLE_TX-");
      assertThat(response.status()).isEqualTo(TransactionStatus.PENDING);

      workflowClient.verify(
          () -> WorkflowClient.start(anyFunc1(), any(GenericTransactionRequest.class)));

      assertThat(captured.get()).isNotNull();
      assertThat(captured.get().activityCode()).isEqualTo("SAMPLE_TX");
      assertThat(captured.get().input().params()).containsKey("name");
      assertThat(captured.get().input().params()).doesNotContainKey("unknownParam");
    }
  }

  @Test
  @DisplayName("shouldIncludeAllParamsWhenAllAreDefined")
  void shouldIncludeAllParamsWhenAllAreDefined() {
    TransactionExecutionRequest request = new TransactionExecutionRequest(
        "SAMPLE_TX", "admin1", Map.of("name", "Alice", "amount", 100));

    ParameterDefinition paramDef1 = mock(ParameterDefinition.class);
    when(paramDef1.getName()).thenReturn("name");

    ParameterDefinition paramDef2 = mock(ParameterDefinition.class);
    when(paramDef2.getName()).thenReturn("amount");

    when(dslRegistry.resolveTransaction("SAMPLE_TX")).thenReturn(mockTransactionDefinition);
    when(mockTransactionDefinition.getParameters()).thenReturn(List.of(paramDef1, paramDef2));
    doReturn(Object.class).when(specRegistry).getActivityInterface("SAMPLE_TX");

    GenericWorkflow mockWorkflow = mock(GenericWorkflow.class);
    when(workflowManager.newWorkflowStub(eq(GenericWorkflow.class), anyString()))
        .thenReturn(mockWorkflow);

    AtomicReference<GenericTransactionRequest> captured = new AtomicReference<>();
    try (MockedStatic<WorkflowClient> workflowClient = mockStatic(WorkflowClient.class)) {
      workflowClient
          .when(() -> WorkflowClient.start(anyFunc1(), any(GenericTransactionRequest.class)))
          .thenAnswer(invocation -> {
            captured.set(invocation.getArgument(1));
            return WorkflowExecution.newBuilder().build();
          });

      transactionRunner.perform(request);

      assertThat(captured.get()).isNotNull();
      assertThat(captured.get().input().params()).hasSize(2);
      assertThat(captured.get().input().params()).containsEntry("name", "Alice");
      assertThat(captured.get().input().params()).containsEntry("amount", 100);
    }
  }

  @Test
  @DisplayName("shouldHandleEmptyParams")
  void shouldHandleEmptyParams() {
    TransactionExecutionRequest request =
        new TransactionExecutionRequest("SAMPLE_TX", "admin1", Map.of());

    when(dslRegistry.resolveTransaction("SAMPLE_TX")).thenReturn(mockTransactionDefinition);
    when(mockTransactionDefinition.getParameters()).thenReturn(List.of());
    doReturn(Object.class).when(specRegistry).getActivityInterface("SAMPLE_TX");

    GenericWorkflow mockWorkflow = mock(GenericWorkflow.class);
    when(workflowManager.newWorkflowStub(eq(GenericWorkflow.class), anyString()))
        .thenReturn(mockWorkflow);

    AtomicReference<GenericTransactionRequest> captured = new AtomicReference<>();
    try (MockedStatic<WorkflowClient> workflowClient = mockStatic(WorkflowClient.class)) {
      workflowClient
          .when(() -> WorkflowClient.start(anyFunc1(), any(GenericTransactionRequest.class)))
          .thenAnswer(invocation -> {
            captured.set(invocation.getArgument(1));
            return WorkflowExecution.newBuilder().build();
          });

      TransactionExecutionResponse response = transactionRunner.perform(request);

      assertThat(response).isNotNull();
      assertThat(response.executionId()).startsWith("transaction-SAMPLE_TX-");
      assertThat(response.status()).isEqualTo(TransactionStatus.PENDING);

      assertThat(captured.get()).isNotNull();
      assertThat(captured.get().input().params()).isEmpty();
    }
  }

  @Test
  @DisplayName("shouldThrowWhenActivityNotRegistered")
  void shouldThrowWhenActivityNotRegistered() {
    TransactionExecutionRequest request =
        new TransactionExecutionRequest("UNKNOWN_TX", "admin1", Map.of("param", "value"));

    doThrow(new IllegalArgumentException("Activity 'UNKNOWN_TX' not found"))
        .when(specRegistry)
        .getActivityInterface("UNKNOWN_TX");

    assertThatThrownBy(() -> transactionRunner.perform(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("UNKNOWN_TX");
  }

  @Test
  @DisplayName("shouldThrowWhenTransactionNotInRegistry")
  void shouldThrowWhenTransactionNotInRegistry() {
    TransactionExecutionRequest request =
        new TransactionExecutionRequest("MISSING_TX", "admin1", Map.of());

    when(dslRegistry.resolveTransaction("MISSING_TX"))
        .thenThrow(new IllegalArgumentException("Transaction 'MISSING_TX' not found"));

    assertThatThrownBy(() -> transactionRunner.perform(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MISSING_TX");
  }

  @Test
  @DisplayName("shouldPreservePerformedByInFilteredRequest")
  void shouldPreservePerformedByInFilteredRequest() {
    TransactionExecutionRequest request = new TransactionExecutionRequest(
        "SAMPLE_TX", "specific-performer", Map.of("extra", "value"));

    ParameterDefinition paramDef = mock(ParameterDefinition.class);
    when(paramDef.getName()).thenReturn("name");

    when(dslRegistry.resolveTransaction("SAMPLE_TX")).thenReturn(mockTransactionDefinition);
    when(mockTransactionDefinition.getParameters()).thenReturn(List.of(paramDef));
    doReturn(Object.class).when(specRegistry).getActivityInterface("SAMPLE_TX");

    GenericWorkflow mockWorkflow = mock(GenericWorkflow.class);
    when(workflowManager.newWorkflowStub(eq(GenericWorkflow.class), anyString()))
        .thenReturn(mockWorkflow);

    AtomicReference<GenericTransactionRequest> captured = new AtomicReference<>();
    try (MockedStatic<WorkflowClient> workflowClient = mockStatic(WorkflowClient.class)) {
      workflowClient
          .when(() -> WorkflowClient.start(anyFunc1(), any(GenericTransactionRequest.class)))
          .thenAnswer(invocation -> {
            captured.set(invocation.getArgument(1));
            return WorkflowExecution.newBuilder().build();
          });

      transactionRunner.perform(request);

      assertThat(captured.get()).isNotNull();
      assertThat(captured.get().input().params()).doesNotContainKey("extra");
    }
  }

  @Test
  @DisplayName("shouldPreserveTransactionCodeInFilteredRequest")
  void shouldPreserveTransactionCodeInFilteredRequest() {
    TransactionExecutionRequest request =
        new TransactionExecutionRequest("MY_TX_CODE", "admin", Map.of("extra", "value"));

    ParameterDefinition paramDef = mock(ParameterDefinition.class);
    when(paramDef.getName()).thenReturn("name");

    when(dslRegistry.resolveTransaction("MY_TX_CODE")).thenReturn(mockTransactionDefinition);
    when(mockTransactionDefinition.getParameters()).thenReturn(List.of(paramDef));
    doReturn(Object.class).when(specRegistry).getActivityInterface("MY_TX_CODE");

    GenericWorkflow mockWorkflow = mock(GenericWorkflow.class);
    when(workflowManager.newWorkflowStub(eq(GenericWorkflow.class), anyString()))
        .thenReturn(mockWorkflow);

    AtomicReference<GenericTransactionRequest> captured = new AtomicReference<>();
    try (MockedStatic<WorkflowClient> workflowClient = mockStatic(WorkflowClient.class)) {
      workflowClient
          .when(() -> WorkflowClient.start(anyFunc1(), any(GenericTransactionRequest.class)))
          .thenAnswer(invocation -> {
            captured.set(invocation.getArgument(1));
            return WorkflowExecution.newBuilder().build();
          });

      transactionRunner.perform(request);

      assertThat(captured.get()).isNotNull();
      assertThat(captured.get().activityCode()).isEqualTo("MY_TX_CODE");
    }
  }
}
