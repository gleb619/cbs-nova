package cbs.app.temporal.massop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.dsl.api.LockDefinition;
import cbs.dsl.api.MassOperationDefinition;
import cbs.dsl.api.SourceDefinition;
import cbs.dsl.api.context.MassOperationContext;
import cbs.dsl.runtime.DslRegistry;
import cbs.nova.entity.MassOperationExecutionEntity;
import cbs.nova.entity.MassOperationItemEntity;
import cbs.nova.entity.MassOperationStatus;
import cbs.nova.repository.MassOperationExecutionRepository;
import cbs.nova.repository.MassOperationItemRepository;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

class MassOpWorkflowImplTest {

  private static final String TASK_QUEUE = "WORKFLOW_TASK_QUEUE";
  private TestWorkflowEnvironment testEnv;
  private DslRegistry dslRegistry;
  private MassOperationExecutionRepository executionRepository;
  private MassOperationItemRepository itemRepository;

  @BeforeEach
  void setUp() {
    testEnv = TestWorkflowEnvironment.newInstance();
    dslRegistry = mock(DslRegistry.class);
    executionRepository = mock(MassOperationExecutionRepository.class);
    itemRepository = mock(MassOperationItemRepository.class);
  }

  @AfterEach
  void tearDown() {
    testEnv.close();
  }

  private void registerWorker() {
    Worker worker = testEnv.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationFactory(
        MassOpWorkflow.class,
        () -> new MassOpWorkflowImpl(
            dslRegistry, executionRepository, itemRepository, new ObjectMapper()));
    worker.registerActivitiesImplementations(new MassOpItemActivityImpl(
        dslRegistry, new ObjectMapper(), executionRepository, itemRepository));
    testEnv.start();
  }

  private MassOpWorkflow newStub() {
    return testEnv
        .getWorkflowClient()
        .newWorkflowStub(
            MassOpWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build());
  }

  private MassOperationDefinition mockMassOpDef(
      String code,
      String category,
      List<Map<String, Object>> dataset,
      LockDefinition lock,
      boolean itemBlockThrows) {
    MassOperationDefinition def = mock(MassOperationDefinition.class);
    when(def.getCode()).thenReturn(code);
    when(def.getCategory()).thenReturn(category);

    SourceDefinition source = mock(SourceDefinition.class);
    when(source.load(any(MassOperationContext.class))).thenReturn(dataset);
    when(def.getSource()).thenReturn(source);

    when(def.getLock()).thenReturn(lock);

    @SuppressWarnings("unchecked")
    Function1<MassOperationContext, Unit> contextBlock =
        (Function1<MassOperationContext, Unit>) mock(Function1.class);
    when(def.getContextBlock()).thenReturn(contextBlock);

    @SuppressWarnings("unchecked")
    Function1<MassOperationContext, Unit> itemBlock =
        (Function1<MassOperationContext, Unit>) mock(Function1.class);
    if (itemBlockThrows) {
      when(itemBlock.invoke(any(MassOperationContext.class)))
          .thenThrow(new RuntimeException("item failed"));
    } else {
      when(itemBlock.invoke(any(MassOperationContext.class))).thenReturn(Unit.INSTANCE);
    }
    when(def.getItemBlock()).thenReturn(itemBlock);

    return def;
  }

  @Test
  @DisplayName("should return DONE when all items succeed")
  void shouldReturnDoneWhenAllItemsSucceed() {
    // Given
    String massOpCode = "TEST_MASS_OP";
    MassOperationDefinition def = mockMassOpDef(
        massOpCode, "test", List.of(Map.of("id", "item1"), Map.of("id", "item2")), null, false);
    when(dslRegistry.getMassOperations()).thenReturn(Map.of(massOpCode, def));

    MassOperationExecutionEntity savedExecution = new MassOperationExecutionEntity();
    savedExecution.setId(1L);
    savedExecution.setCode(massOpCode);
    savedExecution.setCategory("test");
    savedExecution.setDslVersion("1.0");
    savedExecution.setStatus(MassOperationStatus.RUNNING);
    savedExecution.setContext("{}");
    savedExecution.setTotalItems(2L);
    savedExecution.setProcessedCount(0L);
    savedExecution.setFailedCount(0L);
    savedExecution.setTriggerType("MANUAL");
    savedExecution.setPerformedBy("testUser");
    savedExecution.setTemporalWorkflowId("wf-1");

    when(executionRepository.save(any(MassOperationExecutionEntity.class)))
        .thenAnswer(invocation -> {
          MassOperationExecutionEntity entity = invocation.getArgument(0);
          if (entity.getId() == null) {
            entity.setId(1L);
          }
          return entity;
        });
    when(itemRepository.save(any(MassOperationItemEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    registerWorker();
    MassOpWorkflow workflow = newStub();

    // When
    MassOpInput input = new MassOpInput(massOpCode, "testUser", "1.0", "{}");
    MassOpResult result = workflow.execute(input);

    // Then
    assertEquals("DONE", result.status());
    assertEquals(2L, result.totalItems());
    assertEquals(2L, result.successCount());
    assertEquals(0L, result.failureCount());
  }

  @Test
  @DisplayName("should return DONE_WITH_FAILURES when some items fail")
  void shouldReturnDoneWithFailuresWhenSomeItemsFail() {
    // Given
    String massOpCode = "TEST_MASS_OP_PARTIAL";

    MassOperationDefinition def = mockMassOpDef(
        massOpCode, "test", List.of(Map.of("id", "item1"), Map.of("id", "item2")), null, false);
    when(dslRegistry.getMassOperations()).thenReturn(Map.of(massOpCode, def));

    // Make itemRepository.save track items and simulate one failure via a custom activity
    when(executionRepository.save(any(MassOperationExecutionEntity.class)))
        .thenAnswer(invocation -> {
          MassOperationExecutionEntity entity = invocation.getArgument(0);
          if (entity.getId() == null) {
            entity.setId(2L);
          }
          return entity;
        });
    when(itemRepository.save(any(MassOperationItemEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Register a custom activity that fails on the second item
    MassOpItemActivityImpl failingActivity =
        new MassOpItemActivityImpl(
            dslRegistry, new ObjectMapper(), executionRepository, itemRepository) {
          private int callCount = 0;

          @Override
          public MassOpItemResult processItem(MassOpItemInput input) {
            callCount++;
            if (callCount == 2) {
              return new MassOpItemResult(false, "item failed");
            }
            return super.processItem(input);
          }
        };

    Worker worker = testEnv.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationFactory(
        MassOpWorkflow.class,
        () -> new MassOpWorkflowImpl(
            dslRegistry, executionRepository, itemRepository, new ObjectMapper()));
    worker.registerActivitiesImplementations(failingActivity);
    testEnv.start();

    MassOpWorkflow workflow = newStub();

    // When
    MassOpInput input = new MassOpInput(massOpCode, "testUser", "1.0", "{}");
    MassOpResult result = workflow.execute(input);

    // Then
    assertEquals("DONE_WITH_FAILURES", result.status());
    assertEquals(2L, result.totalItems());
    assertEquals(1L, result.successCount());
    assertEquals(1L, result.failureCount());
  }

  @Test
  @DisplayName("should return FAULTED when all items fail")
  void shouldReturnFaultedWhenAllItemsFail() {
    // Given
    String massOpCode = "TEST_MASS_OP_FAULT";
    MassOperationDefinition def = mockMassOpDef(
        massOpCode,
        "test",
        List.of(Map.of("id", "item1"), Map.of("id", "item2")),
        null,
        true); // itemBlock throws
    when(dslRegistry.getMassOperations()).thenReturn(Map.of(massOpCode, def));

    when(executionRepository.save(any(MassOperationExecutionEntity.class)))
        .thenAnswer(invocation -> {
          MassOperationExecutionEntity entity = invocation.getArgument(0);
          if (entity.getId() == null) {
            entity.setId(3L);
          }
          return entity;
        });
    when(itemRepository.save(any(MassOperationItemEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    registerWorker();
    MassOpWorkflow workflow = newStub();

    // When
    MassOpInput input = new MassOpInput(massOpCode, "testUser", "1.0", "{}");
    MassOpResult result = workflow.execute(input);

    // Then
    assertEquals("FAULTED", result.status());
    assertEquals(2L, result.totalItems());
    assertEquals(0L, result.successCount());
    assertEquals(2L, result.failureCount());
  }

  @Test
  @DisplayName("should return LOCKED when lock is active")
  void shouldReturnLockedWhenLockIsActive() {
    // Given
    String massOpCode = "TEST_MASS_OP_LOCK";
    LockDefinition lock = mock(LockDefinition.class);
    when(lock.isLocked(any(MassOperationContext.class))).thenReturn(true);

    MassOperationDefinition def =
        mockMassOpDef(massOpCode, "test", List.of(Map.of("id", "item1")), lock, false);
    when(dslRegistry.getMassOperations()).thenReturn(Map.of(massOpCode, def));

    when(executionRepository.save(any(MassOperationExecutionEntity.class)))
        .thenAnswer(invocation -> {
          MassOperationExecutionEntity entity = invocation.getArgument(0);
          if (entity.getId() == null) {
            entity.setId(4L);
          }
          return entity;
        });

    registerWorker();
    MassOpWorkflow workflow = newStub();

    // When
    MassOpInput input = new MassOpInput(massOpCode, "testUser", "1.0", "{}");
    MassOpResult result = workflow.execute(input);

    // Then
    assertEquals("LOCKED", result.status());
    assertEquals(0L, result.totalItems());
    assertEquals(0L, result.successCount());
    assertEquals(0L, result.failureCount());

    ArgumentCaptor<MassOperationExecutionEntity> captor =
        ArgumentCaptor.forClass(MassOperationExecutionEntity.class);
    verify(executionRepository).save(captor.capture());
    assertEquals(MassOperationStatus.LOCKED, captor.getValue().getStatus());
  }

  @Test
  @DisplayName("should throw ApplicationFailure when massOpCode is unknown")
  void shouldThrowApplicationFailureWhenMassOpCodeUnknown() {
    // Given
    when(dslRegistry.getMassOperations()).thenReturn(Map.of());

    registerWorker();
    MassOpWorkflow workflow = newStub();

    // When / Then
    MassOpInput input = new MassOpInput("UNKNOWN_CODE", "testUser", "1.0", "{}");
    assertThrows(WorkflowFailedException.class, () -> workflow.execute(input));
  }
}
