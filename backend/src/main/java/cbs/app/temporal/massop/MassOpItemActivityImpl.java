package cbs.app.temporal.massop;

import cbs.dsl.api.MassOperationDefinition;
import cbs.dsl.api.context.MassOperationContext;
import cbs.dsl.runtime.DslRegistry;
import cbs.nova.entity.MassOperationExecutionEntity;
import cbs.nova.entity.MassOperationItemEntity;
import cbs.nova.repository.MassOperationExecutionRepository;
import cbs.nova.repository.MassOperationItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MassOpItemActivityImpl implements MassOpItemActivity {

  private final DslRegistry dslRegistry;
  private final ObjectMapper objectMapper;
  private final MassOperationExecutionRepository executionRepository;
  private final MassOperationItemRepository itemRepository;

  @Override
  public MassOpItemResult processItem(MassOpItemInput input) {
    // 5a. Look up MassOperationDefinition
    MassOperationDefinition massOpDef = dslRegistry.getMassOperations().get(input.massOpCode());
    if (massOpDef == null) {
      return new MassOpItemResult(false, "MassOp not found: " + input.massOpCode());
    }

    // 5b. Build context
    @SuppressWarnings("unchecked")
    Map<String, Object> itemData;
    try {
      itemData = objectMapper.readValue(input.itemDataJson(), Map.class);
    } catch (JacksonException e) {
      return new MassOpItemResult(false, "Invalid item data JSON: " + e.getMessage());
    }

    MassOperationContext ctx = new MassOperationContext(
        input.itemId(),
        itemData,
        input.massOperationExecutionId(),
        input.performedBy(),
        input.dslVersion());

    // 5c. Call itemBlock
    try {
      massOpDef.getItemBlock().invoke(ctx);
      // 5e. On success
      return new MassOpItemResult(true, null);
    } catch (Exception e) {
      // 5d. On exception
      return new MassOpItemResult(false, e.getMessage());
    }
  }

  @Override
  public void persistItem(MassOpItemPersistInput input) {
    log.debug("Persisting mass operation item: key={}, status={}", input.itemKey(), input.status());

    MassOperationItemEntity item = new MassOperationItemEntity();
    item.setMassOperationExecution(input.execution());
    item.setItemKey(input.itemKey());
    item.setItemData(input.itemDataJson());
    item.setStatus(input.status());
    item.setErrorMessage(input.errorMessage());
    item.setStartedAt(OffsetDateTime.now());
    item.setCompletedAt(OffsetDateTime.now());
    itemRepository.save(item);
  }

  @Override
  public void updateCounts(MassOpCountsUpdateInput input) {
    log.debug(
        "Updating mass operation counts: successCount={}, failureCount={}",
        input.successCount(),
        input.failureCount());

    input.execution().setProcessedCount(input.successCount() + input.failureCount());
    input.execution().setFailedCount(input.failureCount());
    executionRepository.save(input.execution());
  }

  @Override
  public MassOperationExecutionEntity createLockedExecution(MassOpExecutionCreateInput input) {
    log.debug("Creating locked mass operation execution: code={}", input.massOpCode());

    MassOperationExecutionEntity execution = new MassOperationExecutionEntity();
    execution.setCode(input.massOpCode());
    execution.setCategory(input.category());
    execution.setDslVersion(input.dslVersion());
    execution.setStatus(input.status());
    execution.setContext(input.contextJson());
    execution.setTotalItems(0L);
    execution.setProcessedCount(0L);
    execution.setFailedCount(0L);
    execution.setTriggerType("MANUAL");
    execution.setPerformedBy(input.performedBy());
    execution.setStartedAt(OffsetDateTime.now());
    execution.setTemporalWorkflowId(input.temporalWorkflowId());
    return executionRepository.save(execution);
  }

  @Override
  public MassOperationExecutionEntity createRunningExecution(MassOpExecutionCreateInput input) {
    log.debug(
        "Creating running mass operation execution: code={}, totalItems={}",
        input.massOpCode(),
        input.totalItems());

    MassOperationExecutionEntity execution = new MassOperationExecutionEntity();
    execution.setCode(input.massOpCode());
    execution.setCategory(input.category());
    execution.setDslVersion(input.dslVersion());
    execution.setStatus(input.status());
    execution.setContext(input.contextJson());
    execution.setTotalItems(input.totalItems());
    execution.setProcessedCount(0L);
    execution.setFailedCount(0L);
    execution.setTriggerType("MANUAL");
    execution.setPerformedBy(input.performedBy());
    execution.setStartedAt(OffsetDateTime.now());
    execution.setTemporalWorkflowId(input.temporalWorkflowId());
    return executionRepository.save(execution);
  }
}
