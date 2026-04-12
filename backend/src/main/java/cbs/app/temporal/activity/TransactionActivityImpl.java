package cbs.app.temporal.activity;

import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.context.TransactionContext;
import cbs.dsl.runtime.DslRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TransactionActivityImpl implements TransactionActivity {

  private static final Logger LOG = LoggerFactory.getLogger(TransactionActivityImpl.class);

  private final DslRegistry dslRegistry;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public TransactionActivityImpl(DslRegistry dslRegistry) {
    this.dslRegistry = dslRegistry;
  }

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
        LOG.warn(
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
      return objectMapper.readValue(contextJson, new TypeReference<>() {
      });
    } catch (JsonProcessingException e) {
      return Map.of();
    }
  }
}
