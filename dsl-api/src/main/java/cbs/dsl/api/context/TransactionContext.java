package cbs.dsl.api.context;

import lombok.Builder;

import java.util.Map;
import java.util.function.BiFunction;

@Builder(toBuilder = true)
public record TransactionContext<T>(
    String eventNumber,
    String performedBy,
    Map<String, Object> params,
    HelperResolver helperResolver,
    T payload) {

  @FunctionalInterface
  public interface HelperResolver extends BiFunction<String, HelperContext<?>, HelperContext<?>> {}


}
