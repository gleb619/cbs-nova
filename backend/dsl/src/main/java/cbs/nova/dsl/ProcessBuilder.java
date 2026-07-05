package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ProcessBuilder {
  private final String name;
  private String taskQueue;
  private String version = "v1";
  private Class<?> inputType;
  private Class<?> outputType;
  private List<ParameterDescriptor> parameters;
  private Function<ProcessContext<?>, Result<?>> executeLogic;
  @Nullable
  private Function<CompensationContext<?>, Result<?>> compensationLogic;

  ProcessBuilder(@NonNull String name) {
    this.name = name;
    this.taskQueue = name + "-queue";
  }

  public ProcessBuilder input(@NonNull Class<?> type) {
    this.inputType = type;
    return this;
  }
  public ProcessBuilder output(@NonNull Class<?> type) {
    this.outputType = type;
    return this;
  }
  public ProcessBuilder parameters(@NonNull Consumer<ParameterRegistry> registrar) {
    var registry = new DefaultParameterRegistry();
    registrar.accept(registry);
    this.parameters = registry.descriptors();
    return this;
  }
  public ProcessBuilder taskQueue(@NonNull String queue) {
    this.taskQueue = queue;
    return this;
  }
  public ProcessBuilder version(@NonNull String version) {
    this.version = version;
    return this;
  }
  public ProcessBuilder execute(@NonNull Function<ProcessContext<?>, Result<?>> logic) {
    this.executeLogic = logic;
    return this;
  }
  public ProcessBuilder compensation(@NonNull Function<CompensationContext<?>, Result<?>> logic) {
    this.compensationLogic = logic;
    return this;
  }

  public @NonNull ProcessDslObject build() {
    if (executeLogic == null)
      throw new IllegalStateException("execute() is required for process: " + name);
    if (parameters != null && (inputType != null || outputType != null))
      throw new IllegalStateException(
              "process '" + name + "' cannot have both .parameters() and .input()/.output()");
    return new ProcessDslObject(name, taskQueue, version, inputType, outputType, parameters,
            executeLogic, compensationLogic);
  }

  public @NonNull List<DslObject> buildList() {
    return List.of(build());
  }
}
