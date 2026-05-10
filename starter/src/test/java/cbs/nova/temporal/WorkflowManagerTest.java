package cbs.nova.temporal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cbs.dsl.api.EventOperation;
import cbs.dsl.api.EventTypes.EventInput;
import cbs.dsl.api.EventTypes.EventOutput;
import cbs.dsl.api.SpecDefinitionRegistry;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

@ExtendWith(MockitoExtension.class)
class WorkflowManagerTest {

  @Mock
  private SpecDefinitionRegistry artifactRegistry;

  @Mock
  private WorkflowClient workflowClient;

  @InjectMocks
  private WorkflowManager workflowManager;

  interface SampleWorkflow extends EventOperation {

    EventOutput execute(EventInput input);

  }

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(workflowManager, "taskQueue", "TEST_QUEUE");
  }

  @Test
  @DisplayName("shouldCreateWorkflowStubWithCorrectOptions")
  void shouldCreateWorkflowStubWithCorrectOptions() {
    doReturn(SampleWorkflow.class).when(artifactRegistry).getWorkflowInterface("LOAN_123");
    SampleWorkflow expectedStub = mock(SampleWorkflow.class);
    doReturn(expectedStub).when(workflowClient).newWorkflowStub(eq(SampleWorkflow.class), any(WorkflowOptions.class));

    SampleWorkflow result = workflowManager.newWorkflowStub("LOAN_123", "wf-1");

    assertThat(result).isSameAs(expectedStub);
    ArgumentCaptor<WorkflowOptions> captor = ArgumentCaptor.forClass(WorkflowOptions.class);
    verify(workflowClient).newWorkflowStub(eq(SampleWorkflow.class), captor.capture());
    WorkflowOptions options = captor.getValue();
    assertThat(options.getWorkflowId()).isEqualTo("wf-1");
    assertThat(options.getTaskQueue()).isEqualTo("TEST_QUEUE");
  }

  @Test
  @DisplayName("shouldCreateUntypedWorkflowStubWithCorrectOptions")
  void shouldCreateUntypedWorkflowStubWithCorrectOptions() {
    WorkflowStub expectedStub = mock(WorkflowStub.class);
    doReturn(expectedStub).when(workflowClient).newUntypedWorkflowStub(any(String.class), any(WorkflowOptions.class));

    WorkflowStub result = workflowManager.newUntypedWorkflowStub("LOAN_456", "wf-2");

    assertThat(result).isSameAs(expectedStub);
    ArgumentCaptor<WorkflowOptions> optionsCaptor = ArgumentCaptor.forClass(WorkflowOptions.class);
    ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
    verify(workflowClient).newUntypedWorkflowStub(codeCaptor.capture(), optionsCaptor.capture());
    WorkflowOptions options = optionsCaptor.getValue();
    assertThat(codeCaptor.getValue()).isEqualTo("LOAN_456");
    assertThat(options.getWorkflowId()).isEqualTo("wf-2");
    assertThat(options.getTaskQueue()).isEqualTo("TEST_QUEUE");
  }

  @Test
  @DisplayName("shouldUseDefaultTaskQueueWhenTaskQueueIsBlank")
  void shouldUseDefaultTaskQueueWhenTaskQueueIsBlank() {
    ReflectionTestUtils.setField(workflowManager, "taskQueue", "");

    WorkflowStub expectedStub = mock(WorkflowStub.class);
    doReturn(expectedStub).when(workflowClient).newUntypedWorkflowStub(any(String.class), any(WorkflowOptions.class));

    workflowManager.newUntypedWorkflowStub("EVENT_CODE", "wf-3");

    ArgumentCaptor<WorkflowOptions> optionsCaptor = ArgumentCaptor.forClass(WorkflowOptions.class);
    verify(workflowClient).newUntypedWorkflowStub(eq("EVENT_CODE"), optionsCaptor.capture());
    assertThat(optionsCaptor.getValue().getTaskQueue()).isEqualTo("cbs-nova-task-queue");
  }

  @Test
  @DisplayName("shouldDelegateGetWorkflowInterfaceToRegistry")
  void shouldDelegateGetWorkflowInterfaceToRegistry() {
    doReturn(SampleWorkflow.class).when(artifactRegistry).getWorkflowInterface("LOAN_123");
    assertThat(workflowManager.getWorkflowInterface("LOAN_123")).isEqualTo(SampleWorkflow.class);
  }

  @Test
  @DisplayName("shouldDelegateGetWorkflowCodesToRegistry")
  void shouldDelegateGetWorkflowCodesToRegistry() {
    when(artifactRegistry.getWorkflowCodes()).thenReturn(Set.of("A", "B"));
    assertThat(workflowManager.getWorkflowCodes()).containsExactlyInAnyOrder("A", "B");
  }

  @Test
  @DisplayName("shouldDelegateGetWorkflowToRegistry")
  void shouldDelegateGetWorkflowToRegistry() {
    SampleWorkflow impl = mock(SampleWorkflow.class);
    when(artifactRegistry.getWorkflow("LOAN_123", SampleWorkflow.class)).thenReturn(impl);
    assertThat(workflowManager.getWorkflow("LOAN_123", SampleWorkflow.class)).isSameAs(impl);
  }

  @Test
  @DisplayName("shouldRegisterAllWorkflowsWithWorker")
  void shouldRegisterAllWorkflowsWithWorker() {
    SampleWorkflow impl = mock(SampleWorkflow.class);

    when(artifactRegistry.getWorkflowCodes()).thenReturn(Set.of("WF_1"));
    doReturn(SampleWorkflow.class).when(artifactRegistry).getWorkflowInterface("WF_1");
    when(artifactRegistry.getWorkflow("WF_1", SampleWorkflow.class)).thenReturn(impl);

    Worker worker = mock(Worker.class);
    workflowManager.registerWorkflows(worker);

    verify(worker).registerWorkflowImplementationTypes(impl.getClass());
  }

  @Test
  @DisplayName("shouldNotCallWorkerWhenNoWorkflowsRegistered")
  void shouldNotCallWorkerWhenNoWorkflowsRegistered() {
    when(artifactRegistry.getWorkflowCodes()).thenReturn(Set.of());

    Worker worker = mock(Worker.class);
    workflowManager.registerWorkflows(worker);

    verifyNoInteractions(worker);
  }
}