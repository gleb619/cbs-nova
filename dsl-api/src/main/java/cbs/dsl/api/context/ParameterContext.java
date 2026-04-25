package cbs.dsl.api.context;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.util.Map;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(builderMethodName = "parameterBuilder")
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ParameterContext extends BaseContext {

  private Map<String, Object> eventParameters;

  public ParameterContext(
      String eventCode,
      Long workflowExecutionId,
      String performedBy,
      String dslVersion,
      Map<String, Object> eventParameters) {
    super(eventCode, workflowExecutionId, performedBy, dslVersion);
    this.eventParameters = eventParameters;
  }
}
