package cbs.dsl.api.context;

import java.util.Map;
import lombok.Builder;

@Builder(toBuilder = true)
public record Context(
    String eventNumber,
    String performedBy,
    Map<String, Object> params,
    HelperResolver helperResolver
) {

  public Context put(String key, Object value) {
    params.put(key, value);
    return this;
  }

  public Object get(String key) {
    return params.get(key);
  }

  public Object helper(String key, Map<String, Object> values) {
    return helperResolver.run(key, values);
  }

  public Context copy() {
    return toBuilder().build();
  }

}
