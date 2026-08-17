package cbs.nova.starter.services;

import cbs.nova.dsl.GeneratedClassDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.exception.DslExecutionException;
import cbs.nova.dsl.model.MapInput;
import cbs.nova.dsl.process.DslTemporalProcess;
import cbs.nova.dsl.process.DslTemporalProcessRequest;
import cbs.nova.starter.converter.MapInputConverter;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.worker.WorkerFactory;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class TemporalDslService {

  private final WorkflowClient workflowClient;
  private final MapInputConverter mapInputConverter;
  private final WorkerFactory workerFactory;

  @Builder
  public record DslExecutionRequest(
          @NonNull String code,
          @NonNull Object input,
          @NonNull Class<?> outputType,
          @Nullable WorkflowOptions options,
          @Nullable String runId) {
  }

  public <O> O execute(
          @NonNull String code, @NonNull Object input, @NonNull Class<O> outputType) {
    return execute(DslExecutionRequest.builder()
            .code(code).input(input).outputType(outputType).build());
  }

  public <O> O execute(@NonNull DslExecutionRequest request) {
    GeneratedClassDescriptor descriptor = resolveProcess(request.code());
    WorkflowOptions effectiveOptions = request.options() != null
            ? request.options()
            : defaultOptions(descriptor);

    Object preparedInput = prepareInput(request.input(), descriptor.inputType());
    String runId = request.runId() != null ? request.runId() : effectiveOptions.getWorkflowId();

    var stub = workflowClient.newWorkflowStub(descriptor.temporalInterface(), effectiveOptions);

    if (!(stub instanceof DslTemporalProcess)) {
      throw new DslExecutionException(runId,
              "Stub is not a DslTemporalProcess: " + stub.getClass().getName(), null);
    }
    @SuppressWarnings("unchecked")
    DslTemporalProcess<Object> process = (DslTemporalProcess<Object>) stub;

    try {
      Object result = process.execute(new DslTemporalProcessRequest<>(runId, preparedInput));
      @SuppressWarnings("unchecked")
      O cast = (O) request.outputType().cast(result);
      return cast;
    } catch (RuntimeException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      throw new DslExecutionException(runId,
              "DSL workflow " + request.code() + " failed: " + cause.getMessage(), cause);
    }
  }

  public <O> O execute(
          @NonNull String code,
          @NonNull Map<String, Object> parameters,
          @NonNull Class<O> outputType) {
    return execute(code, (Object) parameters, outputType);
  }

  public void close() {
    workerFactory.shutdown();
  }

  private GeneratedClassDescriptor resolveProcess(String code) {
    return GlobalManager.globalManager().findGeneratedProcess(code)
            .orElseThrow(() -> new IllegalArgumentException("No generated DSL process: " + code));
  }

  private Object prepareInput(Object input, Class<?> inputType) {
    if (inputType != null) {
      if (input instanceof MapInput(Map<String, Object> values)) {
        return mapInputConverter.convert(values, inputType);
      }
      if (input instanceof Map<?, ?> map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = (Map<String, Object>) map;
        return mapInputConverter.convert(parameters, inputType);
      }
    }
    return input;
  }

  private WorkflowOptions defaultOptions(GeneratedClassDescriptor descriptor) {
    return WorkflowOptions.newBuilder()
            .setWorkflowId(descriptor.name() + "-" + UUID.randomUUID())
            .setTaskQueue(descriptor.taskQueue())
            .setWorkflowExecutionTimeout(Duration.ofSeconds(30))
            .setWorkflowTaskTimeout(Duration.ofSeconds(5))
            .build();
  }
}
