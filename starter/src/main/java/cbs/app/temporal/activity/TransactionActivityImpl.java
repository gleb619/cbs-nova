package cbs.app.temporal.activity;

import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.TransactionTypes.TransactionInput;
import cbs.dsl.runtime.DslRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

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

    // 2. Build TransactionInput
    Map<String, Object> contextMap = parseContextJson(input.contextJson());
    TransactionInput txnInput = new TransactionInput(
        contextMap, input.transactionCode(), null, String.valueOf(input.workflowExecutionId()));

    // 3. Execute preview + execute, with rollback on failure
    try {
      txDef.preview(txnInput);
      txDef.execute(txnInput);
      return new TransactionResult(true, null);
    } catch (Exception e) {
      try {
        txDef.rollback(txnInput);
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
