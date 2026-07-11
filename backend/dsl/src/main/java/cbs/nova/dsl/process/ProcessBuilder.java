package cbs.nova.dsl.process;

import cbs.nova.dsl.CompensationContext;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.ParameterDescriptor;
import cbs.nova.dsl.ParameterRegistry;
import cbs.nova.dsl.ProcessContext;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.registry.DefaultParameterRegistry;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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
  @Nullable
  private Function<ProcessContext<?>, Result<?>> previewLogic;
  @Nullable
  private Supplier<DslDescriptor> descriptor;

  public ProcessBuilder(@NonNull String name) {
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
  public ProcessBuilder preview(@NonNull Function<ProcessContext<?>, Result<?>> logic) {
    this.previewLogic = logic;
    return this;
  }
  public ProcessBuilder describe(@NonNull Supplier<DslDescriptor> desc) {
    this.descriptor = desc;
    return this;
  }

  public @NonNull ProcessDslObject build() {
    if (executeLogic == null)
      throw new IllegalStateException("execute() is required for process: " + name);
    if (parameters != null && (inputType != null || outputType != null))
      throw new IllegalStateException(
              "process '" + name + "' cannot have both .parameters() and .input()/.output()");
    return new ProcessDslObject(name, taskQueue, version, inputType, outputType, parameters,
            executeLogic, compensationLogic, previewLogic, descriptor);
  }

  public @NonNull List<DslObject> buildList() {
    return List.of(build());
  }
}
