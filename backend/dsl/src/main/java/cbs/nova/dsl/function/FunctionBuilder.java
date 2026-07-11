package cbs.nova.dsl.function;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.FunctionContext;
import cbs.nova.dsl.ParameterDescriptor;
import cbs.nova.dsl.ParameterRegistry;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.registry.DefaultParameterRegistry;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class FunctionBuilder {

  private final String name;
  private List<ParameterDescriptor> parameters;
  private Function<FunctionContext<?>, Result<?>> executeLogic;
  @Nullable
  private Function<FunctionContext<?>, Result<?>> previewLogic;
  @Nullable
  private Supplier<DslDescriptor> descriptor;

  public FunctionBuilder(@NonNull String name) {
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

  public FunctionBuilder preview(@NonNull Function<FunctionContext<?>, Result<?>> logic) {
    this.previewLogic = logic;
    return this;
  }

  public FunctionBuilder describe(@NonNull Supplier<DslDescriptor> desc) {
    this.descriptor = desc;
    return this;
  }

  public @NonNull FunctionDslObject build() {
    if (executeLogic == null) {
      throw new IllegalStateException("execute() is required for function: " + name);
    }
    return new FunctionDslObject(name, parameters, executeLogic, previewLogic, descriptor);
  }

  public @NonNull List<DslObject> buildList() {
    return List.of(build());
  }
}
