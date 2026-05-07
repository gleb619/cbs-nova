package cbs.dsl.builder;

import cbs.dsl.api.DslObject;
import cbs.dsl.builder.ContextDslObject.Pair;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ContextBuilder {

  private final Map<String, Object> params = new HashMap<>();

  public ContextBuilder put(String key, Object value) {
    params.put(key, value);

    return this;
  }

  public DslObject build() {
    return new ContextDslObject(params.entrySet().stream()
        .map(entry -> Pair.of(entry.getKey(), entry.getValue()))
        .collect(Collectors.toList())
    );
  }
}
