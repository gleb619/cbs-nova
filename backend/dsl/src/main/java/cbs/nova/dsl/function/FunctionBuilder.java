package cbs.nova.dsl.function;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.FunctionContext;
import cbs.nova.dsl.model.MapInput;
import cbs.nova.dsl.model.MapOutput;
import cbs.nova.dsl.ParameterDescriptor;
import cbs.nova.dsl.registry.ParameterRegistry;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.registry.DefaultParameterRegistry;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class FunctionBuilder<I, O> {

  private final String name;
  private Class<?> inputType;
  private Class<?> outputType;
  private List<ParameterDescriptor> parameters;
  private Function<FunctionContext<I>, Result<?>> executeLogic;
  @Nullable
  private Function<FunctionContext<I>, Result<?>> previewLogic;
  @Nullable
  private Supplier<DslDescriptor> descriptor;

  public FunctionBuilder(@NonNull String name) {
    this.name = name;
  }

  /**
   * Selects the typed branch and fixes the input body type.
   */
  @SuppressWarnings("unchecked")
  public <T> FunctionBuilder<T, O> input(@NonNull Class<T> type) {
    this.inputType = type;
    return (FunctionBuilder<T, O>) this;
  }

  /**
   * Selects the typed branch and fixes the output/result type.
   */
  @SuppressWarnings("unchecked")
  public <T> FunctionBuilder<I, T> output(@NonNull Class<T> type) {
    this.outputType = type;
    return (FunctionBuilder<I, T>) this;
  }

  /**
   * Selects the map/parameter branch. The body is typed as {@link MapInput} and the result is
   * expected to be a {@link MapOutput}.
   */
  @SuppressWarnings("unchecked")
  public FunctionBuilder<MapInput, MapOutput> parameters(
          @NonNull Consumer<ParameterRegistry> registrar) {
    var registry = new DefaultParameterRegistry();
    registrar.accept(registry);
    this.parameters = registry.descriptors();
    return (FunctionBuilder<MapInput, MapOutput>) this;
  }

  public FunctionBuilder<I, O> execute(@NonNull Function<FunctionContext<I>, Result<?>> logic) {
    this.executeLogic = logic;
    return this;
  }

  public FunctionBuilder<I, O> preview(@NonNull Function<FunctionContext<I>, Result<?>> logic) {
    this.previewLogic = logic;
    return this;
  }

  public FunctionBuilder<I, O> describe(@NonNull Supplier<DslDescriptor> desc) {
    this.descriptor = desc;
    return this;
  }

  public @NonNull FunctionDslObject build() {
    if (executeLogic == null) {
      throw new IllegalStateException("execute() is required for function: " + name);
    }
    if (parameters != null && (inputType != null || outputType != null)) {
      throw new IllegalStateException(
              "function '" + name + "' cannot have both .parameters() and .input()/.output()");
    }
    return new FunctionDslObject(
            name,
            parameters,
            rawExecute(),
            rawPreview(),
            descriptor);
  }

  public @NonNull List<DslObject> buildList() {
    return List.of(build());
  }

  @SuppressWarnings("unchecked")
  private @NonNull Function<FunctionContext<?>, Result<?>> rawExecute() {
    return (Function<FunctionContext<?>, Result<?>>) (Function<?, ?>) executeLogic;
  }

  @SuppressWarnings("unchecked")
  private @Nullable Function<FunctionContext<?>, Result<?>> rawPreview() {
    return previewLogic == null
            ? null
            : (Function<FunctionContext<?>, Result<?>>) (Function<?, ?>) previewLogic;
  }
}
