package cbs.dsl.api.context;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

@Getter
@Setter
@ToString
@EqualsAndHashCode
public class FinishContext extends EnrichmentContext {

  @Builder(builderMethodName = "finishBuilder")
  public FinishContext(
      String eventCode,
      Long workflowExecutionId,
      String performedBy,
      String dslVersion,
      Map<String, Object> eventParameters) {
    super(eventCode, workflowExecutionId, performedBy, dslVersion, eventParameters);
  }
}
