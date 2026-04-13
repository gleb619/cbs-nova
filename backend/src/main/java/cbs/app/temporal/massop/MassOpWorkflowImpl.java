package cbs.app.temporal.massop;

import cbs.dsl.api.MassOperationDefinition;
import cbs.dsl.api.context.MassOperationContext;
import cbs.dsl.runtime.DslRegistry;
import cbs.nova.entity.MassOperationExecutionEntity;
import cbs.nova.entity.MassOperationItemStatus;
import cbs.nova.entity.MassOperationStatus;
import cbs.nova.repository.MassOperationExecutionRepository;
import cbs.nova.repository.MassOperationItemRepository;
import io.temporal.activity.ActivityOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class MassOpWorkflowImpl implements MassOpWorkflow {

  private final DslRegistry dslRegistry;
  private final MassOperationExecutionRepository executionRepository;
  private final MassOperationItemRepository itemRepository;
  private final ObjectMapper objectMapper;

  // Workflow-level signal field
  private MassOpSignal pendingSignal = null;

  // TODO(T15): move persistence to activity for replay safety
  @Override
  public MassOpResult execute(MassOpInput input) {
    log.debug(
        "Starting mass operation execution for code: {}, performedBy: {}",
        input.massOpCode(),
        input.performedBy());

    // 2a. Look up MassOperationDefinition
    MassOperationDefinition massOpDef = findMassOperationDefinition(input.massOpCode());

    // 2b. Build context and call contextBlock (context enrichment)
    MassOperationContext ctx = buildContext(input, massOpDef);

    // 2c. Create activity stub
    MassOpItemActivity activityStub = Workflow.newActivityStub(
        MassOpItemActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(60))
            .build());

    // 2d. Check lock
    if (massOpDef.getLock() != null && massOpDef.getLock().isLocked(ctx)) {
      return handleLockScenario(input, massOpDef, ctx, activityStub);
    }

    // 2e. Load dataset
    List<Map<String, Object>> dataset = loadDataset(massOpDef, ctx);

    // 2f. Persist RUNNING execution entity
    MassOperationExecutionEntity execution =
        createRunningExecution(input, massOpDef, dataset.size(), activityStub);

    // 2g. Process all items
    long[] counts = processDatasetItems(input, execution, dataset, activityStub);
    long successCount = counts[0];
    long failureCount = counts[1];

    // 2h. Handle pending LOCK signal
    handlePendingSignals(execution);

    // 2i. Set final status
    finalizeExecutionStatus(execution, successCount, failureCount);

    // 2j. Return result
    MassOpResult result = new MassOpResult(
        execution.getId(),
        execution.getStatus().name(),
        execution.getTotalItems(),
        successCount,
        failureCount);
    log.debug(
        "Completed mass operation execution with result: status={}, totalItems={}, successCount={}, failureCount={}",
        result.status(),
        result.totalItems(),
        result.successCount(),
        result.failureCount());
    return result;
  }

  @Override
  public void signal(MassOpSignal signal) {
    log.debug("Received mass operation signal: type={}", signal.type());
    this.pendingSignal = signal;
  }

  private MassOperationDefinition findMassOperationDefinition(String massOpCode) {
    MassOperationDefinition massOpDef = dslRegistry.getMassOperations().get(massOpCode);
    if (massOpDef == null) {
      throw ApplicationFailure.newFailure("MassOp not found: " + massOpCode, "NOT_FOUND");
    }
    return massOpDef;
  }

  private MassOperationContext buildContext(MassOpInput input, MassOperationDefinition massOpDef) {
    MassOperationContext ctx =
        new MassOperationContext("", Map.of(), 0L, input.performedBy(), input.dslVersion());
    massOpDef.getContextBlock().invoke(ctx);
    return ctx;
  }

  private MassOpResult handleLockScenario(
      MassOpInput input,
      MassOperationDefinition massOpDef,
      MassOperationContext ctx,
      MassOpItemActivity activityStub) {
    MassOpExecutionCreateInput createInput = new MassOpExecutionCreateInput(
        input.massOpCode(),
        massOpDef.getCategory(),
        input.dslVersion(),
        MassOperationStatus.LOCKED,
        input.contextJson(),
        0L,
        input.performedBy(),
        Workflow.getInfo().getWorkflowId());

    MassOperationExecutionEntity lockedExecution = activityStub.createLockedExecution(createInput);
    return new MassOpResult(lockedExecution.getId(), "LOCKED", 0L, 0L, 0L);
  }

  private List<Map<String, Object>> loadDataset(
      MassOperationDefinition massOpDef, MassOperationContext ctx) {
    return massOpDef.getSource().load(ctx);
  }

  private MassOperationExecutionEntity createRunningExecution(
      MassOpInput input,
      MassOperationDefinition massOpDef,
      int totalItems,
      MassOpItemActivity activityStub) {
    MassOpExecutionCreateInput createInput = new MassOpExecutionCreateInput(
        input.massOpCode(),
        massOpDef.getCategory(),
        input.dslVersion(),
        MassOperationStatus.RUNNING,
        input.contextJson(),
        totalItems,
        input.performedBy(),
        Workflow.getInfo().getWorkflowId());

    return activityStub.createRunningExecution(createInput);
  }

  private long[] processDatasetItems(
      MassOpInput input,
      MassOperationExecutionEntity execution,
      List<Map<String, Object>> dataset,
      MassOpItemActivity activityStub) {
    long successCount = 0;
    long failureCount = 0;

    for (Map<String, Object> itemData : dataset) {
      // Build item input
      String itemDataJson;
      try {
        itemDataJson = objectMapper.writeValueAsString(itemData);
      } catch (JacksonException e) {
        failureCount++;
        MassOpItemPersistInput persistInput = new MassOpItemPersistInput(
            execution, "unknown", "{}", MassOperationItemStatus.FAILED, e.getMessage());
        activityStub.persistItem(persistInput);
        MassOpCountsUpdateInput countsInput =
            new MassOpCountsUpdateInput(execution, successCount, failureCount);
        activityStub.updateCounts(countsInput);
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
        MassOpItemPersistInput persistInput = new MassOpItemPersistInput(
            execution, itemInput.itemId(), itemDataJson, MassOperationItemStatus.DONE, null);
        activityStub.persistItem(persistInput);
      } else {
        failureCount++;
        MassOpItemPersistInput persistInput = new MassOpItemPersistInput(
            execution,
            itemInput.itemId(),
            itemDataJson,
            MassOperationItemStatus.FAILED,
            itemResult.errorMessage());
        activityStub.persistItem(persistInput);
      }

      MassOpCountsUpdateInput countsInput =
          new MassOpCountsUpdateInput(execution, successCount, failureCount);
      activityStub.updateCounts(countsInput);
    }

    return new long[]{successCount, failureCount};
  }

  private void handlePendingSignals(MassOperationExecutionEntity execution) {
    if (pendingSignal != null && pendingSignal.type() == MassOpSignalType.LOCK) {
      execution.setStatus(MassOperationStatus.LOCKED);
      executionRepository.save(execution);
      Workflow.await(
          () -> pendingSignal != null && pendingSignal.type() == MassOpSignalType.UNLOCK);
      pendingSignal = null;
    }
  }

  private void finalizeExecutionStatus(
      MassOperationExecutionEntity execution, long successCount, long failureCount) {
    if (failureCount == 0) {
      execution.setStatus(MassOperationStatus.DONE);
    } else if (successCount > 0) {
      execution.setStatus(MassOperationStatus.DONE_WITH_FAILURES);
    } else {
      execution.setStatus(MassOperationStatus.FAULTED);
    }
    execution.setCompletedAt(OffsetDateTime.now());
    executionRepository.save(execution);
  }
}
