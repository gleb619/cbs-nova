package cbs.nova.dsl.process;

import cbs.nova.dsl.CompensationContext;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.ParameterDescriptor;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.MapInput;
import cbs.nova.dsl.model.MapOutput;
import cbs.nova.dsl.registry.DefaultParameterRegistry;
import cbs.nova.dsl.registry.ParameterRegistry;
import cbs.nova.dsl.transaction.TransactionExecution;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ProcessBuilder<I, O> {

  private final String name;
  private String taskQueue;
  private String version = "v1";
  private Class<?> inputType;
  private Class<?> outputType;
  private List<ParameterDescriptor> parameters;
  private Function<ProcessContext<I>, Result<?>> executeLogic;
  @Nullable
  private Function<CompensationContext<I>, Result<?>> compensationLogic;
  @Nullable
  private Function<ProcessContext<I>, Result<?>> previewLogic;
  @Nullable
  private BiConsumer<CompensationContext<I>, List<TransactionExecution>> userCompensationHandler;
  private List<String> transactionRefs = List.of();
  @Nullable
  private Supplier<DslDescriptor> descriptor;

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
          @NonNull Function<CompensationContext<I>, Result<?>> logic) {
    this.compensationLogic = logic;
    return this;
  }

  public ProcessBuilder<I, O> compensation(
          @NonNull BiConsumer<CompensationContext<I>, List<TransactionExecution>> handler) {
    this.userCompensationHandler = handler;
    return this;
  }

  public ProcessBuilder<I, O> transactions(@NonNull List<String> refs) {
    this.transactionRefs = refs;
    return this;
  }

  public ProcessBuilder<I, O> preview(@NonNull Function<ProcessContext<I>, Result<?>> logic) {
    this.previewLogic = logic;
    return this;
  }

  public ProcessBuilder<I, O> describe(@NonNull Supplier<DslDescriptor> desc) {
    this.descriptor = desc;
    return this;
  }

  public @NonNull ProcessDslObject build() {
    if (executeLogic == null) {
      throw new IllegalStateException("execute() is required for process: " + name);
    }
    if (parameters != null && (inputType != null || outputType != null)) {
      throw new IllegalStateException(
              "process '" + name + "' cannot have both .parameters() and .input()/.output()");
    }
    return new ProcessDslObject(
            name,
            taskQueue,
            version,
            inputType,
            outputType,
            parameters,
            rawExecute(),
            rawCompensation(),
            rawPreview(),
            descriptor,
            rawUserCompensationHandler(),
            transactionRefs);
  }

  public @NonNull List<DslObject> buildList() {
    return List.of(build());
  }

  @SuppressWarnings("unchecked")
  private @NonNull Function<ProcessContext<?>, Result<?>> rawExecute() {
    return (Function<ProcessContext<?>, Result<?>>) (Function<?, ?>) executeLogic;
  }

  @SuppressWarnings("unchecked")
  private @Nullable Function<CompensationContext<?>, Result<?>> rawCompensation() {
    return compensationLogic == null
            ? null
            : (Function<CompensationContext<?>, Result<?>>) (Function<?, ?>) compensationLogic;
  }

  @SuppressWarnings("unchecked")
  private @Nullable Function<ProcessContext<?>, Result<?>> rawPreview() {
    return previewLogic == null
            ? null
            : (Function<ProcessContext<?>, Result<?>>) (Function<?, ?>) previewLogic;
  }

  @SuppressWarnings("unchecked")
  private @Nullable BiConsumer<CompensationContext<?>, List<TransactionExecution>> rawUserCompensationHandler() {
    return userCompensationHandler == null
            ? null
            : (BiConsumer<CompensationContext<?>, List<TransactionExecution>>) (BiConsumer<?, ?>) userCompensationHandler;
  }
}
