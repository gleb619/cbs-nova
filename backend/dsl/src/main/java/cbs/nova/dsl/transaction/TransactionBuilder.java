package cbs.nova.dsl.transaction;

import cbs.nova.dsl.CompensationContext;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.model.MapInput;
import cbs.nova.dsl.model.MapOutput;
import cbs.nova.dsl.ParameterDescriptor;
import cbs.nova.dsl.registry.ParameterRegistry;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.RetryPolicy;
import cbs.nova.dsl.registry.DefaultParameterRegistry;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class TransactionBuilder<I, O> {

  private final String name;
  private String taskQueue;
  private String version = "v1";
  private Class<?> inputType;
  private Class<?> outputType;
  private List<ParameterDescriptor> parameters;
  private Function<TransactionContext<I>, Result<?>> executeLogic;
  @Nullable
  private Function<CompensationContext<I>, Result<?>> compensationLogic;
  private Duration startToCloseTimeout = Duration.ofSeconds(30);
  @Nullable
  private RetryPolicy retryPolicy;
  @Nullable
  private Duration heartbeatTimeout;
  @Nullable
  private Function<TransactionContext<I>, Result<?>> previewLogic;
  @Nullable
  private Supplier<DslDescriptor> descriptor;

  public TransactionBuilder(@NonNull String name) {
    this.name = name;
    this.taskQueue = name + "-queue";
  }

  /**
   * Selects the typed branch and fixes the input body type.
   */
  @SuppressWarnings("unchecked")
  public <T> TransactionBuilder<T, O> input(@NonNull Class<T> type) {
    this.inputType = type;
    return (TransactionBuilder<T, O>) this;
  }

  /**
   * Selects the typed branch and fixes the output/result type.
   */
  @SuppressWarnings("unchecked")
  public <T> TransactionBuilder<I, T> output(@NonNull Class<T> type) {
    this.outputType = type;
    return (TransactionBuilder<I, T>) this;
  }

  /**
   * Selects the map/parameter branch. The body is typed as {@link MapInput} and the result is
   * expected to be a {@link MapOutput}.
   */
  @SuppressWarnings("unchecked")
  public TransactionBuilder<MapInput, MapOutput> parameters(
          @NonNull Consumer<ParameterRegistry> registrar) {
    var registry = new DefaultParameterRegistry();
    registrar.accept(registry);
    this.parameters = registry.descriptors();
    return (TransactionBuilder<MapInput, MapOutput>) this;
  }

  public TransactionBuilder<I, O> taskQueue(@NonNull String queue) {
    this.taskQueue = queue;
    return this;
  }

  public TransactionBuilder<I, O> version(@NonNull String version) {
    this.version = version;
    return this;
  }

  public TransactionBuilder<I, O> execute(
          @NonNull Function<TransactionContext<I>, Result<?>> logic) {
    this.executeLogic = logic;
    return this;
  }

  public TransactionBuilder<I, O> compensation(
          @NonNull Function<CompensationContext<I>, Result<?>> logic) {
    this.compensationLogic = logic;
    return this;
  }

  public TransactionBuilder<I, O> startToCloseTimeout(@NonNull Duration duration) {
    this.startToCloseTimeout = duration;
    return this;
  }

  public TransactionBuilder<I, O> retryPolicy(@NonNull RetryPolicy policy) {
    this.retryPolicy = policy;
    return this;
  }

  public TransactionBuilder<I, O> heartbeatTimeout(@NonNull Duration duration) {
    this.heartbeatTimeout = duration;
    return this;
  }

  public TransactionBuilder<I, O> preview(
          @NonNull Function<TransactionContext<I>, Result<?>> logic) {
    this.previewLogic = logic;
    return this;
  }

  public TransactionBuilder<I, O> describe(@NonNull Supplier<DslDescriptor> desc) {
    this.descriptor = desc;
    return this;
  }

  public @NonNull TransactionDslObject build() {
    if (executeLogic == null) {
      throw new IllegalStateException("execute() is required for transaction: " + name);
    }
    if (parameters != null && (inputType != null || outputType != null)) {
      throw new IllegalStateException(
              "transaction '" + name + "' cannot have both .parameters() and .input()/.output()");
    }
    return new TransactionDslObject(
            name,
            taskQueue,
            version,
            inputType,
            outputType,
            parameters,
            rawExecute(),
            rawCompensation(),
            startToCloseTimeout,
            retryPolicy,
            heartbeatTimeout,
            rawPreview(),
            descriptor);
  }

  public @NonNull List<DslObject> buildList() {
    return List.of(build());
  }

  @SuppressWarnings("unchecked")
  private @NonNull Function<TransactionContext<?>, Result<?>> rawExecute() {
    return (Function<TransactionContext<?>, Result<?>>) (Function<?, ?>) executeLogic;
  }

  @SuppressWarnings("unchecked")
  private @Nullable Function<CompensationContext<?>, Result<?>> rawCompensation() {
    return compensationLogic == null
            ? null
            : (Function<CompensationContext<?>, Result<?>>) (Function<?, ?>) compensationLogic;
  }

  @SuppressWarnings("unchecked")
  private @Nullable Function<TransactionContext<?>, Result<?>> rawPreview() {
    return previewLogic == null
            ? null
            : (Function<TransactionContext<?>, Result<?>>) (Function<?, ?>) previewLogic;
  }
}
