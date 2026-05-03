package cbs.dsl.api.context;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class DisplayScope {

  private List<Map.Entry<String, Object>> labels = new ArrayList<>();

  public void label(String key, Object value) {
    labels.add(new AbstractMap.SimpleEntry<>(key, value));
  }

  public List<Map.Entry<String, Object>> getLabels() {
    return labels;
  }
}
