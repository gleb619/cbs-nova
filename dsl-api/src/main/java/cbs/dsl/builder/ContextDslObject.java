package cbs.dsl.builder;

import cbs.dsl.api.DslObject;
import cbs.dsl.api.HelperDefinition;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ContextDslObject implements DslObject {

  private final List<Pair<String, Object>> parameters;

  @Override
  public String getCode() {
    return "context";
  }

  public record Pair<K, V>(K key, V value) {

    public static <A, B> Pair<A, B> of(A key, B value) {
      return new Pair<>(key, value);
    }

  }

}
