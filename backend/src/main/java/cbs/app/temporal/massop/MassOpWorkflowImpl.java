package cbs.app.temporal.massop;

import cbs.dsl.api.MassOperationDefinition;
import cbs.dsl.api.context.MassOperationContext;
import cbs.dsl.runtime.DslRegistry;
import cbs.nova.entity.MassOperationExecutionEntity;
import cbs.nova.entity.MassOperationItemEntity;
import cbs.nova.entity.MassOperationItemStatus;
import cbs.nova.entity.MassOperationStatus;
import cbs.nova.repository.MassOperationExecutionRepository;
import cbs.nova.repository.MassOperationItemRepository;
import io.temporal.activity.ActivityOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public class MassOpWorkflowImpl implements MassOpWorkflow {

  private static final Logger LOG = LoggerFactory.getLogger(MassOpWorkflowImpl.class);

  private final DslRegistry dslRegistry;
  private final MassOperationExecutionRepository executionRepository;
  private final MassOperationItemRepository itemRepository;

  // Workflow-level signal field
  private MassOpSignal pendingSignal = null;

  public MassOpWorkflowImpl(
      DslRegistry dslRegistry,
      MassOperationExecutionRepository executionRepository,
      MassOperationItemRepository itemRepository) {
    this.dslRegistry = dslRegistry;
    this.executionRepository = executionRepository;
    this.itemRepository = itemRepository;
  }

  // TODO(T15): move persistence to activity for replay safety
  @Override
  public MassOpResult execute(MassOpInput input) {
    // 2a. Look up MassOperationDefinition
    MassOperationDefinition massOpDef = dslRegistry.getMassOperations().get(input.massOpCode());
    if (massOpDef == null) {
      throw ApplicationFailure.newFailure("MassOp not found: " + input.massOpCode(), "NOT_FOUND");
    }

    // 2b. Build context and call contextBlock (context enrichment)
    MassOperationContext ctx =
        new MassOperationContext("", Map.of(), 0L, input.performedBy(), input.dslVersion());
    massOpDef.getContextBlock().invoke(ctx);

    // 2c. Check lock
    if (massOpDef.getLock() != null && massOpDef.getLock().isLocked(ctx)) {
      MassOperationExecutionEntity lockedExecution = new MassOperationExecutionEntity();
      lockedExecution.setCode(input.massOpCode());
      lockedExecution.setCategory(massOpDef.getCategory());
      lockedExecution.setDslVersion(input.dslVersion());
      lockedExecution.setStatus(MassOperationStatus.LOCKED);
      lockedExecution.setContext(input.contextJson());
      lockedExecution.setTotalItems(0L);
      lockedExecution.setProcessedCount(0L);
      lockedExecution.setFailedCount(0L);
      lockedExecution.setTriggerType("MANUAL");
      lockedExecution.setPerformedBy(input.performedBy());
      lockedExecution.setStartedAt(OffsetDateTime.now());
      lockedExecution.setTemporalWorkflowId(Workflow.getInfo().getWorkflowId());
      lockedExecution = executionRepository.save(lockedExecution);

      return new MassOpResult(lockedExecution.getId(), "LOCKED", 0L, 0L, 0L);
    }

    // 2d. Load dataset
    List<Map<String, Object>> dataset = massOpDef.getSource().load(ctx);

    // 2e. Persist RUNNING execution entity
    OffsetDateTime now = OffsetDateTime.now();
    MassOperationExecutionEntity execution = new MassOperationExecutionEntity();
    execution.setCode(input.massOpCode());
    execution.setCategory(massOpDef.getCategory());
    execution.setDslVersion(input.dslVersion());
    execution.setStatus(MassOperationStatus.RUNNING);
    execution.setContext(input.contextJson());
    execution.setTotalItems((long) dataset.size());
    execution.setProcessedCount(0L);
    execution.setFailedCount(0L);
    execution.setTriggerType("MANUAL");
    execution.setPerformedBy(input.performedBy());
    execution.setStartedAt(now);
    execution.setTemporalWorkflowId(Workflow.getInfo().getWorkflowId());
    execution = executionRepository.save(execution);

    // 2f. Create activity stub
    MassOpItemActivity activityStub = Workflow.newActivityStub(
        MassOpItemActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(60))
            .build());

    long successCount = 0;
    long failureCount = 0;

    for (Map<String, Object> itemData : dataset) {
      // Build item input
      String itemDataJson;
      try {
        itemDataJson =
            new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(itemData);
      } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        failureCount++;
        persistItem(execution, "unknown", "{}", MassOperationItemStatus.FAILED, e.getMessage());
        updateCounts(execution, successCount, failureCount);
        continue;
      }

      MassOpItemInput itemInput = new MassOpItemInput(
          itemData.getOrDefault("id", itemData.toString()).toString(),
          itemDataJson,
          input.massOpCode(),
          execution.getId(),
          input.performedBy(),
          input.dslVersion());

      MassOpItemResult itemResult = activityStub.processItem(itemInput);

      if (itemResult.success()) {
        successCount++;
        persistItem(
            execution, itemInput.itemId(), itemDataJson, MassOperationItemStatus.DONE, null);
      } else {
        failureCount++;
        persistItem(
            execution,
            itemInput.itemId(),
            itemDataJson,
            MassOperationItemStatus.FAILED,
            itemResult.errorMessage());
      }

      updateCounts(execution, successCount, failureCount);
    }

    // 2g. Handle pending LOCK signal
    if (pendingSignal != null && pendingSignal.type() == MassOpSignalType.LOCK) {
      execution.setStatus(MassOperationStatus.LOCKED);
      executionRepository.save(execution);
      Workflow.await(
          () -> pendingSignal != null && pendingSignal.type() == MassOpSignalType.UNLOCK);
      pendingSignal = null;
    }

    // 2h. Set final status
    long totalItems = execution.getTotalItems();
    if (failureCount == 0) {
      execution.setStatus(MassOperationStatus.DONE);
    } else if (successCount > 0) {
      execution.setStatus(MassOperationStatus.DONE_WITH_FAILURES);
    } else {
      execution.setStatus(MassOperationStatus.FAULTED);
    }
    execution.setCompletedAt(OffsetDateTime.now());
    executionRepository.save(execution);

    // 2i. Return result
    return new MassOpResult(
        execution.getId(), execution.getStatus().name(), totalItems, successCount, failureCount);
  }

  @Override
  public void signal(MassOpSignal signal) {
    this.pendingSignal = signal;
  }

  // TODO(T15): move persistence to activity for replay safety
  private void persistItem(
      MassOperationExecutionEntity execution,
      String itemKey,
      String itemDataJson,
      MassOperationItemStatus status,
      String errorMessage) {
    MassOperationItemEntity item = new MassOperationItemEntity();
    item.setMassOperationExecution(execution);
    item.setItemKey(itemKey);
    item.setItemData(itemDataJson);
    item.setStatus(status);
    item.setErrorMessage(errorMessage);
    item.setStartedAt(OffsetDateTime.now());
    item.setCompletedAt(OffsetDateTime.now());
    itemRepository.save(item);
  }

  // TODO(T15): move persistence to activity for replay safety
  private void updateCounts(
      MassOperationExecutionEntity execution, long successCount, long failureCount) {
    execution.setProcessedCount(successCount + failureCount);
    execution.setFailedCount(failureCount);
    executionRepository.save(execution);
  }
}
