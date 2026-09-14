package cbs.nova.dsl.function;

import static cbs.nova.dsl.config.DslConstants.DEFAULT_HEARTBEAT_TIMEOUT;
import static cbs.nova.dsl.config.DslConstants.DEFAULT_START_TO_CLOSE_TIMEOUT;
import static cbs.nova.dsl.config.DslConstants.DEFAULT_TASK_QUEUE;
import static cbs.nova.dsl.config.DslConstants.DEFAULT_VERSION;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.FunctionContext;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.ParameterDescriptor;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.explain.ExplainResourceExplainer;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.model.ExplainReports;
import cbs.nova.dsl.model.MapInput;
import cbs.nova.dsl.model.MapOutput;
import cbs.nova.dsl.model.ObjectBuilder;
import cbs.nova.dsl.registry.DefaultParameterRegistry;
import cbs.nova.dsl.registry.ParameterRegistry;
import cbs.nova.dsl.explain.ExplainBudget;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class FunctionBuilder<I, O> implements ObjectBuilder<FunctionDslObject> {

  private final String name;
  private Class<?> inputType;
  private Class<?> outputType;
  private List<ParameterDescriptor> parameters;
  private Function<FunctionContext<I>, Result<?>> executeLogic;
  @Nullable
  private Function<FunctionContext<I>, Result<?>> previewLogic;
  @Nullable
  private Function<FunctionContext<I>, Result<ExplainReport>> explainLogic;
  @Nullable
  private Supplier<DslDescriptor> descriptor;

  public FunctionBuilder(@NonNull String name) {
    this.name = name;
  }

  @SuppressWarnings("unchecked")
  public <T> FunctionBuilder<T, O> input(@NonNull Class<T> type) {
    this.inputType = type;
    return (FunctionBuilder<T, O>) this;
  }

  @SuppressWarnings("unchecked")
  public <T> FunctionBuilder<I, T> output(@NonNull Class<T> type) {
    this.outputType = type;
    return (FunctionBuilder<I, T>) this;
  }

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

  public FunctionBuilder<I, O> explain(
          @NonNull Function<FunctionContext<I>, Result<ExplainReport>> logic) {
    this.explainLogic = logic;
    return this;
  }

  public FunctionBuilder<I, O> explainVia(@NonNull String resourcePath) {
    this.explainLogic = resourceExplain(resourcePath);
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
    var effectiveParameters = parameters != null
            ? parameters
            : List.<ParameterDescriptor>of();
    var effectiveDescriptor = effectiveDescriptor(effectiveParameters);
    var resolvedExecute = rawExecute();
    var explain = rawExplain() != null ? rawExplain() : defaultExplain();
    var resolvedPreview = rawPreview() != null ? rawPreview() : resolvedExecute;
    return FunctionDslObject.builder()
            .name(name)
            .parameters(effectiveParameters)
            .inputType(inputType)
            .outputType(outputType)
            .executeLogic(resolvedExecute)
            .previewLogic(resolvedPreview)
            .explainLogic(explain)
            .descriptor(effectiveDescriptor)
            .build();
  }

  @Override
  public @NonNull List<DslObject> buildList() {
    return List.of(build());
  }

  private @NonNull Supplier<DslDescriptor> effectiveDescriptor(
          @NonNull List<ParameterDescriptor> parameters) {
    return descriptor != null
            ? descriptor
            : () -> defaultDescriptor(
                    name, parameters, inputType, outputType, null);
  }

  // TODO: it's forbidden to `truncateTo`, without traverse a whole graph
  @Deprecated(forRemoval = true)
  private @NonNull Function<FunctionContext<?>, Result<ExplainReport>> defaultExplain() {
    return ctx -> Result.success(
            ExplainReports.truncateTo(
                    ExplainReport.of(name,
                            GlobalManager.globalManager().resolveExplainContent(name)),
                    ExplainBudget.of(ctx)));
  }

  @SuppressWarnings("unchecked")
  private @NonNull Function<FunctionContext<I>, Result<ExplainReport>> resourceExplain(
          @NonNull String resourcePath) {
    return (Function<FunctionContext<I>, Result<ExplainReport>>) (Function<?, ?>) ExplainResourceExplainer
            .viaResource(name, resourcePath);
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

  @SuppressWarnings("unchecked")
  private @Nullable Function<FunctionContext<?>, Result<ExplainReport>> rawExplain() {
    return explainLogic == null
            ? null
            : (Function<FunctionContext<?>, Result<ExplainReport>>) (Function<?, ?>) explainLogic;
  }

  public static @NonNull DslDescriptor defaultDescriptor(
          @NonNull String name,
          @NonNull List<ParameterDescriptor> parameters,
          @Nullable Class<?> inputType,
          @Nullable Class<?> outputType,
          @Nullable String description) {
    var objectDescriptor = FunctionDescriptor.builder()
            .name(name)
            .description(description)
            .inputType(inputType)
            .outputType(outputType)
            .build();
    return DslDescriptor.builder()
            .objectDescriptor(objectDescriptor)
            .hasSideEffects(false)
            .parameters(parameters)
            .taskQueue(DEFAULT_TASK_QUEUE)
            .version(DEFAULT_VERSION)
            .startToCloseTimeout(DEFAULT_START_TO_CLOSE_TIMEOUT)
            .heartbeatTimeout(DEFAULT_HEARTBEAT_TIMEOUT)
            .build();
  }
}
