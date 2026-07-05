package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public final class FunctionBuilder {
  private final String name;
  private List<ParameterDescriptor> parameters;
  private Function<FunctionContext<?>, Result<?>> executeLogic;

  FunctionBuilder(@NonNull String name) {
    this.name = name;
  }

  public FunctionBuilder parameters(@NonNull Consumer<ParameterRegistry> registrar) {
    var registry = new DefaultParameterRegistry();
    registrar.accept(registry);
    this.parameters = registry.descriptors();
    return this;
  }

  public FunctionBuilder execute(@NonNull Function<FunctionContext<?>, Result<?>> logic) {
    this.executeLogic = logic;
    return this;
  }

  public @NonNull FunctionDslObject build() {
    if (executeLogic == null)
      throw new IllegalStateException("execute() is required for function: " + name);
    return new FunctionDslObject(name, parameters, executeLogic);
  }

  public @NonNull List<DslObject> buildList() {
    return List.of(build());
  }
}
