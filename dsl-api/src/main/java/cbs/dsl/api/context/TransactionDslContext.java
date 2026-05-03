package cbs.dsl.api.context;

import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.TransactionTypes.TransactionInput;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Map;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@ToString
@EqualsAndHashCode
public class TransactionDslContext extends TransactionContext {

  private TransactionDefinition delegateTarget;
  private TransactionPhase currentPhase;

  @Builder(builderMethodName = "dslBuilder")
  public TransactionDslContext(
      String eventCode,
      Long workflowExecutionId,
      String performedBy,
      String dslVersion,
      Map<String, Object> eventParameters,
      boolean isResumed,
      TransactionDefinition delegateTarget,
      TransactionPhase currentPhase) {
    super(eventCode, workflowExecutionId, performedBy, dslVersion, eventParameters, isResumed);
    this.delegateTarget = delegateTarget;
    this.currentPhase = currentPhase;
  }

  public TransactionDslContext(
      TransactionContext source, TransactionDefinition delegateTarget, TransactionPhase phase) {
    this(
        source.getEventCode(),
        source.getWorkflowExecutionId(),
        source.getPerformedBy(),
        source.getDslVersion(),
        source.getEventParameters(),
        source.isResumed(),
        delegateTarget,
        phase);
    getEnrichment().putAll(source.getEnrichment());
  }

  private TransactionInput toInput() {
    return new TransactionInput(
        getEventParameters(), getEventCode(), getWorkflowExecutionId().toString());
  }

  @Override
  public void delegate() {
    TransactionInput input = toInput();
    if (delegateTarget == null) {
      return;
    }

    switch (currentPhase) {
      case PREVIEW -> delegateTarget.preview(input);
      case EXECUTE -> delegateTarget.execute(input);
      case ROLLBACK -> delegateTarget.rollback(input);
    }
  }
}
