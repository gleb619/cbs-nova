package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

public final class TransactionBuilder {
  private final String name;
  private String taskQueue;
  private String version = "v1";
  private Class<?> inputType;
  private Class<?> outputType;
  private Function<TransactionContext<?>, Result<?>> executeLogic;
  @Nullable
  private Function<CompensationContext<?>, Result<?>> compensationLogic;
  private Duration startToCloseTimeout = Duration.ofSeconds(30);
  @Nullable
  private RetryPolicy retryPolicy;

  TransactionBuilder(@NonNull String name) {
    this.name = name;
    this.taskQueue = name + "-queue";
  }

  public TransactionBuilder input(@NonNull Class<?> type) {
    this.inputType = type;
    return this;
  }
  public TransactionBuilder output(@NonNull Class<?> type) {
    this.outputType = type;
    return this;
  }
  public TransactionBuilder taskQueue(@NonNull String queue) {
    this.taskQueue = queue;
    return this;
  }
  public TransactionBuilder version(@NonNull String version) {
    this.version = version;
    return this;
  }
  public TransactionBuilder execute(@NonNull Function<TransactionContext<?>, Result<?>> logic) {
    this.executeLogic = logic;
    return this;
  }
  public TransactionBuilder compensation(
          @NonNull Function<CompensationContext<?>, Result<?>> logic) {
    this.compensationLogic = logic;
    return this;
  }
  public TransactionBuilder startToCloseTimeout(@NonNull Duration duration) {
    this.startToCloseTimeout = duration;
    return this;
  }
  public TransactionBuilder retryPolicy(@NonNull RetryPolicy policy) {
    this.retryPolicy = policy;
    return this;
  }

  public @NonNull TransactionDslObject build() {
    if (executeLogic == null)
      throw new IllegalStateException("execute() is required for transaction: " + name);
    return new TransactionDslObject(name, taskQueue, version, inputType, outputType, executeLogic,
            compensationLogic, startToCloseTimeout, retryPolicy);
  }

  public @NonNull List<DslObject> buildList() {
    return List.of(build());
  }
}
