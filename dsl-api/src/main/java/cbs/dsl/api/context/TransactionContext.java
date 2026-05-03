package cbs.dsl.api.context;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.util.Map;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(builderMethodName = "transactionBuilder")
public class TransactionContext extends EnrichmentContext {

  private boolean isResumed;

  public TransactionContext(
      String eventCode,
      Long workflowExecutionId,
      String performedBy,
      String dslVersion,
      Map<String, Object> eventParameters,
      boolean isResumed) {
    super(eventCode, workflowExecutionId, performedBy, dslVersion, eventParameters);
    this.isResumed = isResumed;
  }

  public void delegate() {}
}
