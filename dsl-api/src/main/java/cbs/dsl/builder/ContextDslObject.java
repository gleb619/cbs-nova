package cbs.dsl.builder;

import cbs.dsl.api.DslObject;
import cbs.dsl.api.context.Pair;
import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record ContextDslObject(List<Pair<String, Object>> parameters) implements DslObject {

  @Override
  public String code() {
    return "context";
  }
}
