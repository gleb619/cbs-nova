package cbs.dsl.api.context;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class MassOperationContext {

  private String performedBy;
  private String dslVersion;
  private Map<String, Object> enrichment = new HashMap<>();

  public void set(String key, Object value) {
    enrichment.put(key, value);
  }

  public Object get(String key) {
    return enrichment.get(key);
  }
}
