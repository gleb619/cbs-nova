package cbs.nova.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.dsl.api.MassOperationDefinition;
import cbs.dsl.api.TriggerDefinition.CronTrigger;
import cbs.dsl.api.TriggerDefinition.EveryTrigger;
import cbs.dsl.runtime.DslRegistry;
import cbs.nova.entity.MassOperationExecutionEntity;
import cbs.nova.entity.MassOperationItemEntity;
import cbs.nova.entity.MassOperationItemStatus;
import cbs.nova.entity.MassOperationStatus;
import cbs.nova.mapper.MassOperationMapper;
import cbs.nova.model.MassOperationDto;
import cbs.nova.model.MassOperationItemDto;
import cbs.nova.model.MassOperationTriggerRequest;
import cbs.nova.model.exception.EntityNotFoundException;
import cbs.nova.repository.MassOperationExecutionRepository;
import cbs.nova.repository.MassOperationItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class MassOperationServiceTest {

  @Mock
  private MassOperationExecutionRepository executionRepository;

  @Mock
  private MassOperationItemRepository itemRepository;

  @Mock
  private MassOperationMapper mapper;

  @Mock
  private MassOpTriggerPort triggerPort;

  @Mock
  private DslRegistry dslRegistry;

  @InjectMocks
  private MassOperationService massOperationService;

  private MassOperationExecutionEntity sampleExecution;
  private MassOperationDto sampleDto;

  @BeforeEach
  void setUp() {
    sampleExecution = MassOperationExecutionEntity.builder()
        .id(1L)
        .code("DAILY_INTEREST")
        .category("LOAN")
        .dslVersion("1.0.0")
        .status(MassOperationStatus.RUNNING)
        .context("{}")
        .totalItems(100L)
        .processedCount(0L)
        .failedCount(0L)
        .triggerType("MANUAL")
        .performedBy("admin")
        .startedAt(OffsetDateTime.now())
        .temporalWorkflowId("massop-DAILY_INTEREST-abc123")
        .build();

    sampleDto = MassOperationDto.builder()
        .id(1L)
        .code("DAILY_INTEREST")
        .category("LOAN")
        .dslVersion("1.0.0")
        .status("RUNNING")
        .totalItems(100L)
        .processedCount(0L)
        .failedCount(0L)
        .triggerType("MANUAL")
        .performedBy("admin")
        .startedAt(sampleExecution.getStartedAt())
        .temporalWorkflowId("massop-DAILY_INTEREST-abc123")
        .build();
  }

  @Test
  @DisplayName("shouldTriggerMassOperationAndReturnRunningDto")
  void shouldTriggerMassOperationAndReturnRunningDto() {
    MassOperationTriggerRequest request = new MassOperationTriggerRequest(
        "DAILY_INTEREST", "admin", "1.0.0", "{\"rate\": 0.05}", null, null);

    when(triggerPort.trigger(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn("massop-DAILY_INTEREST-xyz");

    MassOperationDto result = massOperationService.trigger(request);

    assertNotNull(result);
    assertEquals("DAILY_INTEREST", result.getCode());
    assertEquals("RUNNING", result.getStatus());
    assertNotNull(result.getTemporalWorkflowId());
    assertTrue(result.getTemporalWorkflowId().startsWith("massop-DAILY_INTEREST-"));
    verify(triggerPort)
        .trigger(
            eq("DAILY_INTEREST"), eq("admin"), eq("1.0.0"), eq("{\"rate\": 0.05}"), anyString());
  }

  @Test
  @DisplayName("shouldTriggerWithDefaultContextWhenNullProvided")
  void shouldTriggerWithDefaultContextWhenNullProvided() {
    MassOperationTriggerRequest request =
        new MassOperationTriggerRequest("DAILY_INTEREST", "admin", "1.0.0", null, null, null);

    when(triggerPort.trigger(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn("massop-DAILY_INTEREST-xyz");

    massOperationService.trigger(request);

    verify(triggerPort)
        .trigger(eq("DAILY_INTEREST"), eq("admin"), eq("1.0.0"), eq("{}"), anyString());
  }

  @Test
  @DisplayName("shouldReturnDtoWhenFindByIdExists")
  void shouldReturnDtoWhenFindByIdExists() {
    when(executionRepository.findById(1L)).thenReturn(Optional.of(sampleExecution));
    when(mapper.toDto(sampleExecution)).thenReturn(sampleDto);

    MassOperationDto result = massOperationService.findById(1L);

    assertNotNull(result);
    assertEquals(1L, result.getId());
    assertEquals("DAILY_INTEREST", result.getCode());
  }

  @Test
  @DisplayName("shouldThrowExceptionWhenFindByIdNotFound")
  void shouldThrowExceptionWhenFindByIdNotFound() {
    when(executionRepository.findById(99L)).thenReturn(Optional.empty());

    assertThrows(EntityNotFoundException.class, () -> massOperationService.findById(99L));
  }

  @Test
  @DisplayName("shouldReturnAllDtosOrderedByStartedAtDesc")
  void shouldReturnAllDtosOrderedByStartedAtDesc() {
    MassOperationExecutionEntity older = MassOperationExecutionEntity.builder()
        .id(2L)
        .code("DAILY_INTEREST")
        .category("LOAN")
        .dslVersion("1.0.0")
        .status(MassOperationStatus.DONE)
        .context("{}")
        .totalItems(100L)
        .processedCount(100L)
        .failedCount(0L)
        .triggerType("MANUAL")
        .performedBy("admin")
        .startedAt(OffsetDateTime.now().minusHours(1))
        .build();

    when(executionRepository.findAll()).thenReturn(List.of(older, sampleExecution));
    when(mapper.toDto(sampleExecution)).thenReturn(sampleDto);
    when(mapper.toDto(older))
        .thenReturn(MassOperationDto.builder()
            .id(2L)
            .code("DAILY_INTEREST")
            .status("DONE")
            .startedAt(older.getStartedAt())
            .build());

    List<MassOperationDto> result = massOperationService.findAll();

    assertEquals(2, result.size());
    // First should be the newer one (sorted descending by startedAt)
    assertEquals(1L, result.get(0).getId());
    assertEquals(2L, result.get(1).getId());
  }

  @Test
  @DisplayName("shouldReturnDtosByCodeOrderedByStartedAtDesc")
  void shouldReturnDtosByCodeOrderedByStartedAtDesc() {
    when(executionRepository.findByCode("DAILY_INTEREST")).thenReturn(List.of(sampleExecution));
    when(mapper.toDto(sampleExecution)).thenReturn(sampleDto);

    List<MassOperationDto> result = massOperationService.findByCode("DAILY_INTEREST");

    assertEquals(1, result.size());
    assertEquals("DAILY_INTEREST", result.get(0).getCode());
  }

  @Test
  @DisplayName("shouldReturnItemsWhenExecutionExists")
  void shouldReturnItemsWhenExecutionExists() {
    MassOperationItemEntity item = MassOperationItemEntity.builder()
        .id(10L)
        .itemKey("loan-001")
        .status(MassOperationItemStatus.DONE)
        .startedAt(OffsetDateTime.now())
        .completedAt(OffsetDateTime.now())
        .build();

    MassOperationItemDto itemDto = MassOperationItemDto.builder()
        .id(10L)
        .itemKey("loan-001")
        .status("DONE")
        .startedAt(item.getStartedAt())
        .completedAt(item.getCompletedAt())
        .build();

    when(executionRepository.findById(1L)).thenReturn(Optional.of(sampleExecution));
    when(itemRepository.findByMassOperationExecution(sampleExecution)).thenReturn(List.of(item));
    when(mapper.toItemDto(item)).thenReturn(itemDto);

    List<MassOperationItemDto> result = massOperationService.findItems(1L);

    assertEquals(1, result.size());
    assertEquals("loan-001", result.get(0).getItemKey());
  }

  @Test
  @DisplayName("shouldThrowExceptionWhenFindItemsForNonexistentExecution")
  void shouldThrowExceptionWhenFindItemsForNonexistentExecution() {
    when(executionRepository.findById(99L)).thenReturn(Optional.empty());

    assertThrows(EntityNotFoundException.class, () -> massOperationService.findItems(99L));
  }

  @Test
  @DisplayName("shouldReturnZeroWhenNoFailedItemsToRetry")
  void shouldReturnZeroWhenNoFailedItemsToRetry() {
    when(executionRepository.findById(1L)).thenReturn(Optional.of(sampleExecution));
    when(itemRepository.findByMassOperationExecutionAndStatus(
            sampleExecution, MassOperationItemStatus.FAILED))
        .thenReturn(List.of());

    int result = massOperationService.retryFailedItems(1L);

    assertEquals(0, result);
  }

  @Test
  @DisplayName("shouldRetryFailedItemsAndReturnCount")
  void shouldRetryFailedItemsAndReturnCount() {
    MassOperationItemEntity failedItem = MassOperationItemEntity.builder()
        .id(20L)
        .itemKey("loan-002")
        .status(MassOperationItemStatus.FAILED)
        .errorMessage("timeout")
        .build();

    when(executionRepository.findById(1L)).thenReturn(Optional.of(sampleExecution));
    when(itemRepository.findByMassOperationExecutionAndStatus(
            sampleExecution, MassOperationItemStatus.FAILED))
        .thenReturn(List.of(failedItem));
    when(triggerPort.trigger(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn("massop-retry-1-20");

    int result = massOperationService.retryFailedItems(1L);

    assertEquals(1, result);
    verify(triggerPort)
        .trigger(eq("DAILY_INTEREST"), eq("admin"), eq("1.0.0"), eq("{}"), eq("massop-retry-1-20"));
  }

  @Test
  @DisplayName("shouldThrowExceptionWhenRetryNonexistentExecution")
  void shouldThrowExceptionWhenRetryNonexistentExecution() {
    when(executionRepository.findById(99L)).thenReturn(Optional.empty());

    assertThrows(EntityNotFoundException.class, () -> massOperationService.retryFailedItems(99L));
  }

  @Test
  @DisplayName("shouldFireCronWhenExpressionMatchesWithinLast60Seconds")
  void shouldFireCronWhenExpressionMatchesWithinLast60Seconds() {
    // Use a cron that runs every minute: "0 * * * * *"
    CronTrigger cronTrigger = mock(CronTrigger.class);
    when(cronTrigger.getExpression()).thenReturn("0 * * * * *");

    boolean result = massOperationService.shouldFireCron(cronTrigger);

    // "0 * * * * *" means at second 0 of every minute.
    // Within the last 60 seconds this should match.
    assertTrue(result);
  }

  @Test
  @DisplayName("shouldNotFireCronWhenExpressionIsInvalid")
  void shouldNotFireCronWhenExpressionIsInvalid() {
    CronTrigger cronTrigger = mock(CronTrigger.class);
    when(cronTrigger.getExpression()).thenReturn("invalid-cron");

    boolean result = massOperationService.shouldFireCron(cronTrigger);

    assertFalse(result);
  }

  @Test
  @DisplayName("shouldFireEveryWhenNoPreviousExecutionExists")
  void shouldFireEveryWhenNoPreviousExecutionExists() {
    EveryTrigger everyTrigger = mock(EveryTrigger.class);
    when(everyTrigger.getDays()).thenReturn(0);
    when(everyTrigger.getHours()).thenReturn(0);
    when(everyTrigger.getMinutes()).thenReturn(30);
    when(executionRepository.findByCode("DAILY_INTEREST")).thenReturn(List.of());

    boolean result = massOperationService.shouldFireEvery(everyTrigger, "DAILY_INTEREST");

    assertTrue(result);
  }

  @Test
  @DisplayName("shouldFireEveryWhenLastExecutionIsOlderThanInterval")
  void shouldFireEveryWhenLastExecutionIsOlderThanInterval() {
    EveryTrigger everyTrigger = mock(EveryTrigger.class);
    when(everyTrigger.getDays()).thenReturn(0);
    when(everyTrigger.getHours()).thenReturn(0);
    when(everyTrigger.getMinutes()).thenReturn(30);

    MassOperationExecutionEntity oldExecution = MassOperationExecutionEntity.builder()
        .id(1L)
        .startedAt(OffsetDateTime.now().minusHours(1))
        .build();

    when(executionRepository.findByCode("DAILY_INTEREST")).thenReturn(List.of(oldExecution));

    boolean result = massOperationService.shouldFireEvery(everyTrigger, "DAILY_INTEREST");

    assertTrue(result);
  }

  @Test
  @DisplayName("shouldNotFireEveryWhenLastExecutionIsWithinInterval")
  void shouldNotFireEveryWhenLastExecutionIsWithinInterval() {
    EveryTrigger everyTrigger = mock(EveryTrigger.class);
    when(everyTrigger.getDays()).thenReturn(0);
    when(everyTrigger.getHours()).thenReturn(0);
    when(everyTrigger.getMinutes()).thenReturn(30);

    MassOperationExecutionEntity recentExecution = MassOperationExecutionEntity.builder()
        .id(1L)
        .startedAt(OffsetDateTime.now().minusMinutes(5))
        .build();

    when(executionRepository.findByCode("DAILY_INTEREST")).thenReturn(List.of(recentExecution));

    boolean result = massOperationService.shouldFireEvery(everyTrigger, "DAILY_INTEREST");

    assertFalse(result);
  }

  @Test
  @DisplayName("shouldReturnTrueWhenRunningExecutionExists")
  void shouldReturnTrueWhenRunningExecutionExists() {
    when(executionRepository.findByCode("DAILY_INTEREST")).thenReturn(List.of(sampleExecution));

    boolean result = massOperationService.hasRunningExecution("DAILY_INTEREST");

    assertTrue(result);
  }

  @Test
  @DisplayName("shouldReturnFalseWhenNoRunningExecutionExists")
  void shouldReturnFalseWhenNoRunningExecutionExists() {
    MassOperationExecutionEntity doneExecution = MassOperationExecutionEntity.builder()
        .id(2L)
        .code("DAILY_INTEREST")
        .status(MassOperationStatus.DONE)
        .startedAt(OffsetDateTime.now().minusHours(1))
        .build();

    when(executionRepository.findByCode("DAILY_INTEREST")).thenReturn(List.of(doneExecution));

    boolean result = massOperationService.hasRunningExecution("DAILY_INTEREST");

    assertFalse(result);
  }

  @Test
  @DisplayName("shouldReturnDefinitionWhenCodeExists")
  void shouldReturnDefinitionWhenCodeExists() {
    MassOperationDefinition definition = mock(MassOperationDefinition.class);
    when(definition.getCode()).thenReturn("DAILY_INTEREST");
    when(dslRegistry.getMassOperations()).thenReturn(Map.of("DAILY_INTEREST", definition));

    MassOperationDefinition result = massOperationService.getMassOpDefinition("DAILY_INTEREST");

    assertNotNull(result);
    assertEquals("DAILY_INTEREST", result.getCode());
  }

  @Test
  @DisplayName("shouldReturnNullWhenCodeNotFound")
  void shouldReturnNullWhenCodeNotFound() {
    when(dslRegistry.getMassOperations()).thenReturn(Map.of());

    MassOperationDefinition result = massOperationService.getMassOpDefinition("NONEXISTENT");

    assertNullResult(result);
  }

  @Test
  @DisplayName("shouldReturnAllDefinitions")
  void shouldReturnAllDefinitions() {
    MassOperationDefinition def1 = mock(MassOperationDefinition.class);
    MassOperationDefinition def2 = mock(MassOperationDefinition.class);
    when(dslRegistry.getMassOperations()).thenReturn(Map.of("OP1", def1, "OP2", def2));

    Map<String, MassOperationDefinition> result = massOperationService.getAllMassOpDefinitions();

    assertEquals(2, result.size());
  }

  private void assertNullResult(MassOperationDefinition result) {
    // Helper to avoid nullable assertion issues with Spotless
    if (result != null) {
      throw new AssertionError("Expected null but got: " + result);
    }
  }
}
