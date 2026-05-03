package cbs.nova.service;

import cbs.dsl.api.MassOperationDefinition;
import cbs.dsl.api.TriggerDefinition;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MassOperationService {

  private final MassOperationExecutionRepository executionRepository;
  private final MassOperationItemRepository itemRepository;
  private final MassOperationMapper mapper;
  private final MassOpTriggerPort triggerPort;
  private final DslRegistry dslRegistry;

  @Transactional
  public MassOperationDto trigger(MassOperationTriggerRequest request) {
    String workflowId = "massop-" + request.massOpCode() + "-" + UUID.randomUUID();
    triggerPort.trigger(
        request.massOpCode(),
        request.performedBy(),
        request.dslVersion(),
        request.contextJson() != null ? request.contextJson() : "{}",
        workflowId);

    String triggerType = request.triggerType() != null ? request.triggerType() : "MANUAL";

    log.debug("Triggered mass operation {} with workflow ID {}", request.massOpCode(), workflowId);

    return MassOperationDto.builder()
        .code(request.massOpCode())
        .status("RUNNING")
        .triggerType(triggerType)
        .triggerSource(request.triggerSource())
        .performedBy(request.performedBy())
        .dslVersion(request.dslVersion())
        .temporalWorkflowId(workflowId)
        .startedAt(OffsetDateTime.now())
        .build();
  }

  public MassOperationDto findById(Long id) {
    log.debug("Finding mass operation execution by id: {}", id);
    return executionRepository
        .findById(id)
        .map(mapper::toDto)
        .orElseThrow(() -> new EntityNotFoundException("MassOperationExecution", id));
  }

  public List<MassOperationDto> findAll() {
    log.debug("Finding all mass operation executions");
    return executionRepository.findAll().stream()
        .sorted(Comparator.comparing(MassOperationExecutionEntity::getStartedAt).reversed())
        .map(mapper::toDto)
        .toList();
  }

  public List<MassOperationDto> findByCode(String code) {
    log.debug("Finding mass operation executions by code: {}", code);
    return executionRepository.findByCode(code).stream()
        .sorted(Comparator.comparing(MassOperationExecutionEntity::getStartedAt).reversed())
        .map(mapper::toDto)
        .toList();
  }

  public List<MassOperationItemDto> findItems(Long executionId) {
    log.debug("Finding items for execution id: {}", executionId);
    MassOperationExecutionEntity execution = executionRepository
        .findById(executionId)
        .orElseThrow(() -> new EntityNotFoundException("MassOperationExecution", executionId));
    return itemRepository.findByMassOperationExecution(execution).stream()
        .sorted(Comparator.comparing(MassOperationItemEntity::getId))
        .map(mapper::toItemDto)
        .toList();
  }

  @Transactional
  public int retryFailedItems(Long executionId) {
    log.debug("Retrying failed items for execution id: {}", executionId);
    MassOperationExecutionEntity execution = executionRepository
        .findById(executionId)
        .orElseThrow(() -> new EntityNotFoundException("MassOperationExecution", executionId));

    List<MassOperationItemEntity> failedItems =
        itemRepository.findByMassOperationExecutionAndStatus(
            execution, MassOperationItemStatus.FAILED);

    if (failedItems.isEmpty()) {
      log.debug("No failed items to retry for execution id: {}", executionId);
      return 0;
    }

    int retriedCount = 0;
    for (MassOperationItemEntity failedItem : failedItems) {
      String retryWorkflowId = "massop-retry-" + executionId + "-" + failedItem.getId();
      triggerPort.trigger(
          execution.getCode(),
          execution.getPerformedBy(),
          execution.getDslVersion(),
          execution.getContext(),
          retryWorkflowId);
      retriedCount++;
      log.debug("Retried failed item {} with workflow ID {}", failedItem.getId(), retryWorkflowId);
    }

    return retriedCount;
  }

  /** Checks whether a cron trigger should fire based on the last 60 seconds. */
  boolean shouldFireCron(TriggerDefinition.CronTrigger cronTrigger) {
    try {
      CronExpression cron = CronExpression.parse(cronTrigger.getExpression());
      LocalDateTime now = LocalDateTime.now();
      LocalDateTime prev = now.minusSeconds(60);
      LocalDateTime next = cron.next(prev);
      return next != null && !next.isAfter(now);
    } catch (IllegalArgumentException e) {
      log.warn("Invalid cron expression: {}", cronTrigger.getExpression(), e);
      return false;
    }
  }

  /** Checks whether an every trigger should fire based on the last execution time. */
  boolean shouldFireEvery(TriggerDefinition.EveryTrigger everyTrigger, String code) {
    Duration interval = Duration.ofDays(everyTrigger.getDays())
        .plusHours(everyTrigger.getHours())
        .plusMinutes(everyTrigger.getMinutes());

    List<MassOperationExecutionEntity> recent = executionRepository.findByCode(code);
    if (recent.isEmpty()) {
      return true;
    }

    return recent.stream()
        .map(MassOperationExecutionEntity::getStartedAt)
        .max(Comparator.naturalOrder())
        .map(last -> last.isBefore(OffsetDateTime.now().minus(interval)))
        .orElse(true);
  }

  /** Checks whether there is a currently running execution for the given code. */
  boolean hasRunningExecution(String code) {
    return executionRepository.findByCode(code).stream()
        .anyMatch(e -> e.getStatus() == MassOperationStatus.RUNNING);
  }

  /** Get all DSL mass operation definitions. */
  Map<String, MassOperationDefinition> getAllMassOpDefinitions() {
    return dslRegistry.getMassOperations();
  }

  /** Get the DSL mass operation definition for the given code. */
  MassOperationDefinition getMassOpDefinition(String code) {
    return dslRegistry.getMassOperations().get(code);
  }
}
