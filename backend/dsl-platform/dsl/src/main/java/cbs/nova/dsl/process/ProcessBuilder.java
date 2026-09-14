package cbs.nova.dsl.process;

import cbs.nova.dsl.CompensationContext;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.model.ObjectBuilder;
import cbs.nova.dsl.ParameterDescriptor;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.explain.DescriptorMarkdown;
import cbs.nova.dsl.explain.ExplainResourceExplainer;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.model.ExplainReports;
import cbs.nova.dsl.model.MapInput;
import cbs.nova.dsl.model.MapOutput;
import cbs.nova.dsl.registry.DefaultParameterRegistry;
import cbs.nova.dsl.registry.ParameterRegistry;
import cbs.nova.dsl.explain.ExplainBudget;
import cbs.nova.dsl.transaction.TransactionExecution;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static cbs.nova.dsl.config.DslConstants.DEFAULT_HEARTBEAT_TIMEOUT;
import static cbs.nova.dsl.config.DslConstants.DEFAULT_START_TO_CLOSE_TIMEOUT;

public final class ProcessBuilder<I, O> implements ObjectBuilder<ProcessDslObject> {

  private final String name;
  private String taskQueue;
  private String version = "v1";
  private Class<?> inputType;
  private Class<?> outputType;
  private List<ParameterDescriptor> parameters;
  private Function<ProcessContext<I>, Result<?>> executeLogic;
  @Nullable
  private BiConsumer<CompensationContext<I>, List<TransactionExecution>> compensationLogic;
  @Nullable
  private Function<ProcessContext<I>, Result<?>> previewLogic;
  @Nullable
  private Function<ProcessContext<I>, Result<ExplainReport>> explainLogic;

  public ProcessBuilder(@NonNull String name) {
    this.name = name;
    this.taskQueue = name + "-queue";
  }

  @SuppressWarnings("unchecked")
  public <T> ProcessBuilder<T, O> input(@NonNull Class<T> type) {
    this.inputType = type;
    return (ProcessBuilder<T, O>) this;
  }

  @SuppressWarnings("unchecked")
  public <T> ProcessBuilder<I, T> output(@NonNull Class<T> type) {
    this.outputType = type;
    return (ProcessBuilder<I, T>) this;
  }

  @SuppressWarnings("unchecked")
  public ProcessBuilder<MapInput, MapOutput> parameters(
          @NonNull Consumer<ParameterRegistry> registrar) {
    var registry = new DefaultParameterRegistry();
    registrar.accept(registry);
    this.parameters = registry.descriptors();
    return (ProcessBuilder<MapInput, MapOutput>) this;
  }

  public ProcessBuilder<I, O> taskQueue(@NonNull String queue) {
    this.taskQueue = queue;
    return this;
  }

  public ProcessBuilder<I, O> version(@NonNull String version) {
    this.version = version;
    return this;
  }

  public ProcessBuilder<I, O> execute(@NonNull Function<ProcessContext<I>, Result<?>> logic) {
    this.executeLogic = logic;
    return this;
  }

  public ProcessBuilder<I, O> compensation(
          @NonNull BiConsumer<CompensationContext<I>, List<TransactionExecution>> handler) {
    this.compensationLogic = handler;
    return this;
  }

  public ProcessBuilder<I, O> preview(@NonNull Function<ProcessContext<I>, Result<?>> logic) {
    this.previewLogic = logic;
    return this;
  }

  public ProcessBuilder<I, O> explain(
          @NonNull Function<ProcessContext<I>, Result<ExplainReport>> logic) {
    this.explainLogic = logic;
    return this;
  }

  public ProcessBuilder<I, O> explainVia(@NonNull String resourcePath) {
    this.explainLogic = resourceExplain(resourcePath);
    return this;
  }

  @Override
  public @NonNull ProcessDslObject build() {
    if (executeLogic == null) {
      throw new IllegalStateException("execute() is required for process: " + name);
    }
    if (parameters != null && (inputType != null || outputType != null)) {
      throw new IllegalStateException(
              "process '" + name + "' cannot have both .parameters() and .input()/.output()");
    }
    var descriptor = defaultDescriptor(
            name, taskQueue, version, inputType, outputType,
            parameters != null ? parameters : List.of(),
            compensationLogic != null, null);
    var resolvedExecute = rawExecute();
    var explain = rawExplain() != null ? rawExplain() : defaultExplain(descriptor);
    var resolvedPreview = rawPreview() != null ? rawPreview() : resolvedExecute;
    return ProcessDslObject.builder()
            .name(name)
            .description(Constants.EMPTY_MARKDOWN)
            .taskQueue(taskQueue)
            .version(version)
            .inputType(inputType != null ? inputType : Void.class)
            .outputType(outputType != null ? outputType : Void.class)
            .parameters(parameters != null ? parameters : List.of())
            .executeLogic(resolvedExecute)
            .compensationLogic(rawCompensationLogic())
            .previewLogic(resolvedPreview)
            .explainLogic(explain)
            .descriptor(descriptor)
            .build();
  }

  @Override
  public @NonNull List<DslObject> buildList() {
    return List.of(build());
  }

  // TODO: it's forbidden to `truncateTo`, without traverse a whole graph
  @Deprecated(forRemoval = true)
  private @NonNull Function<ProcessContext<?>, Result<ExplainReport>> defaultExplain(
          @NonNull DslDescriptor descriptor) {
    return ctx -> Result.success(
            ExplainReports.truncateTo(
                    new ExplainReport(name, DescriptorMarkdown.render(descriptor), ""),
                    ExplainBudget.of(ctx)));
  }

  @SuppressWarnings("unchecked")
  private @NonNull Function<ProcessContext<I>, Result<ExplainReport>> resourceExplain(
          @NonNull String resourcePath) {
    return (Function<ProcessContext<I>, Result<ExplainReport>>) (Function<?, ?>) ExplainResourceExplainer
            .viaResource(name, resourcePath);
  }

  @SuppressWarnings("unchecked")
  private @NonNull Function<ProcessContext<?>, Result<?>> rawExecute() {
    return (Function<ProcessContext<?>, Result<?>>) (Function<?, ?>) executeLogic;
  }

  @SuppressWarnings("unchecked")
  private @Nullable BiConsumer<CompensationContext<?>, List<TransactionExecution>> rawCompensationLogic() {
    return compensationLogic == null
            ? null
            : (BiConsumer<CompensationContext<?>, List<TransactionExecution>>) (BiConsumer<?, ?>) compensationLogic;
  }

  @SuppressWarnings("unchecked")
  private @Nullable Function<ProcessContext<?>, Result<?>> rawPreview() {
    return previewLogic == null
            ? null
            : (Function<ProcessContext<?>, Result<?>>) (Function<?, ?>) previewLogic;
  }

  @SuppressWarnings("unchecked")
  private @Nullable Function<ProcessContext<?>, Result<ExplainReport>> rawExplain() {
    return explainLogic == null
            ? null
            : (Function<ProcessContext<?>, Result<ExplainReport>>) (Function<?, ?>) explainLogic;
  }

  public static @NonNull DslDescriptor defaultDescriptor(
          @NonNull String name,
          @NonNull String taskQueue,
          @NonNull String version,
          @Nullable Class<?> inputType,
          @Nullable Class<?> outputType,
          @NonNull List<ParameterDescriptor> parameters,
          boolean hasSideEffects,
          @Nullable String description) {
    var objectDescriptor = ProcessDescriptor.builder()
            .name(name)
            .description(description)
            .version(version)
            .taskQueue(taskQueue)
            .inputType(inputType)
            .outputType(outputType)
            .hasCompensation(hasSideEffects)
            .helperRefs(List.of())
            .transactionRefs(List.of())
            .build();
    return DslDescriptor.builder()
            .objectDescriptor(objectDescriptor)
            .hasSideEffects(hasSideEffects)
            .parameters(parameters)
            .taskQueue(taskQueue)
            .version(version)
            .startToCloseTimeout(DEFAULT_START_TO_CLOSE_TIMEOUT)
            .heartbeatTimeout(DEFAULT_HEARTBEAT_TIMEOUT)
            .build();
  }
}
