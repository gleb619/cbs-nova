package cbs.dsl.api.context;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

@Getter
@Setter
@ToString
@SuperBuilder(builderMethodName = "enrichmentBuilder")
@EqualsAndHashCode
public class EnrichmentContext extends ParameterContext {

  private Map<String, Object> enrichment = new HashMap<>();
  private BiFunction<String, Map<String, Object>, Object> helperResolver;

  public EnrichmentContext(
      String eventCode,
      Long workflowExecutionId,
      String performedBy,
      String dslVersion,
      Map<String, Object> eventParameters) {
    super(eventCode, workflowExecutionId, performedBy, dslVersion, eventParameters);
  }

  public void set(String key, Object value) {
    enrichment.put(key, value);
  }

  public void put(String key, Object value) {
    set(key, value);
  }

  public Object helper(String name, Map<String, Object> params) {
    if (helperResolver != null) {
      return helperResolver.apply(name, params);
    }
    return null;
  }
}
