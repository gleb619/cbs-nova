package cbs.dsl.api.context;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

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

  public Object get(String key) {
    return eventParameters != null ? eventParameters.get(key) : null;
  }

  public Object getOrDefault(String key, Object defaultValue) {
    return eventParameters != null ? eventParameters.getOrDefault(key, defaultValue) : defaultValue;
  }
}
