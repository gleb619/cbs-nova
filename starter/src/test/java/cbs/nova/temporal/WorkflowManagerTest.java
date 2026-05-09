package cbs.nova.temporal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.dsl.api.SpecDefinitionRegistry;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.worker.Worker;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WorkflowManagerTest {

  @Mock
  private SpecDefinitionRegistry artifactRegistry;

  @Mock
  private WorkflowClient workflowClient;

  @InjectMocks
  private WorkflowManager workflowManager;

  interface SampleWorkflow {
    String execute(String input);
  }

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(workflowManager, "taskQueue", "TEST_QUEUE");
  }

  @Test
  @DisplayName("shouldCreateWorkflowStubWithCorrectOptions")
  void shouldCreateWorkflowStubWithCorrectOptions() {
    when(artifactRegistry.getWorkflowInterface("LOAN_123")).thenReturn(SampleWorkflow.class);
    SampleWorkflow expectedStub = mock(SampleWorkflow.class);
    when(workflowClient.newWorkflowStub(eq(SampleWorkflow.class), any(WorkflowOptions.class)))
        .thenReturn(expectedStub);

    SampleWorkflow result = workflowManager.newWorkflowStub("LOAN_123", "wf-1");

    assertThat(result).isSameAs(expectedStub);
    ArgumentCaptor<WorkflowOptions> captor = ArgumentCaptor.forClass(WorkflowOptions.class);
    verify(workflowClient).newWorkflowStub(eq(SampleWorkflow.class), captor.capture());
    WorkflowOptions options = captor.getValue();
    assertThat(options.getWorkflowId()).isEqualTo("wf-1");
    assertThat(options.getTaskQueue()).isEqualTo("TEST_QUEUE");
  }

  @Test
  @DisplayName("shouldDelegateGetWorkflowInterfaceToRegistry")
  void shouldDelegateGetWorkflowInterfaceToRegistry() {
    when(artifactRegistry.getWorkflowInterface("LOAN_123")).thenReturn(SampleWorkflow.class);
    assertThat(workflowManager.getWorkflowInterface("LOAN_123")).isEqualTo(SampleWorkflow.class);
  }

  @Test
  @DisplayName("shouldDelegateGetWorkflowCodesToRegistry")
  void shouldDelegateGetWorkflowCodesToRegistry() {
    when(artifactRegistry.getWorkflowCodes()).thenReturn(Set.of("A", "B"));
    assertThat(workflowManager.getWorkflowCodes()).containsExactly("A", "B");
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
    when(artifactRegistry.getWorkflowInterface("WF_1")).thenReturn(SampleWorkflow.class);
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

    verify(worker).registerWorkflowImplementationTypes();
  }
}
