package cbs.app.temporal.activity;

import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.context.TransactionContext;
import cbs.dsl.runtime.DslRegistry;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionActivityImpl implements TransactionActivity {

  private final DslRegistry dslRegistry;
  private final ObjectMapper objectMapper;

  @Override
  public TransactionResult executeTransaction(TransactionActivityInput input) {
    // 1. Look up transaction definition
    TransactionDefinition txDef = dslRegistry.getTransactions().get(input.transactionCode());
    if (txDef == null) {
      return new TransactionResult(false, "Transaction not found: " + input.transactionCode());
    }

    // 2. Build TransactionContext
    Map<String, Object> contextMap = parseContextJson(input.contextJson());
    TransactionContext ctx = new TransactionContext(
        input.transactionCode(),
        input.workflowExecutionId(),
        input.performedBy(),
        input.dslVersion(),
        contextMap,
        false);

    // 3. Execute preview + execute, with rollback on failure
    try {
      txDef.preview(ctx);
      txDef.execute(ctx);
      return new TransactionResult(true, null);
    } catch (Exception e) {
      try {
        txDef.rollback(ctx);
      } catch (Exception rollbackEx) {
        log.warn(
            "Rollback failed for transaction '{}': {}",
            input.transactionCode(),
            rollbackEx.getMessage(),
            rollbackEx);
      }
      return new TransactionResult(false, e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parseContextJson(String contextJson) {
    try {
      return objectMapper.readValue(contextJson, new TypeReference<>() {});
    } catch (JacksonException e) {
      return Map.of();
    }
  }
}
