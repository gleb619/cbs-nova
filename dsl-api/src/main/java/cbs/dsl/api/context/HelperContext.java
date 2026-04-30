package cbs.dsl.api.context;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class HelperContext extends BaseContext {

  private Map<String, Object> params;
  private Map<String, Object> enrichment = new HashMap<>();

  @Builder(builderMethodName = "helperBuilder")
  public HelperContext(
      String eventCode,
      Long workflowExecutionId,
      String performedBy,
      String dslVersion,
      Map<String, Object> params) {
    super(eventCode, workflowExecutionId, performedBy, dslVersion);
    this.params = params;
  }

  public void set(String key, Object value) {
    enrichment.put(key, value);
  }

  public Object helper(String name, Map<String, Object> params) {
    return null;
  }

  public <T> T resolve(Class<T> clazz) {
    throw new UnsupportedOperationException("HelperContext.resolve not implemented");
  }
}
