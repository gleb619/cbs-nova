package cbs.nova.starter.services;

import cbs.nova.dsl.DslExecutionException;
import cbs.nova.dsl.DslTemporalProcess;
import cbs.nova.dsl.DslTemporalProcessRequest;
import cbs.nova.dsl.GeneratedClassDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.MapInput;
import cbs.nova.dsl.config.DslConfig;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TemporalDslService {

  private final WorkflowClient workflowClient;
  private final Set<String> registeredQueues = ConcurrentHashMap.newKeySet();
  private volatile WorkerFactory workerFactory;
  private volatile boolean started;

  public TemporalDslService(WorkflowClient workflowClient) {
    this.workflowClient = workflowClient;
  }

  private WorkerFactory workerFactory() {
    if (workerFactory == null) {
      synchronized (this) {
        if (workerFactory == null) {
          workerFactory = WorkerFactory.newInstance(workflowClient);
        }
      }
    }
    return workerFactory;
  }

  private void ensureStarted() {
    if (!started) {
      synchronized (this) {
        if (!started) {
          workerFactory().start();
          started = true;
        }
      }
    }
  }

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

    ensureWorker(descriptor);

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
    WorkerFactory f = workerFactory;
    if (f != null) {
      f.shutdown();
    }
  }

  private void ensureWorker(GeneratedClassDescriptor descriptor) {
    if (registeredQueues.add(descriptor.taskQueue())) {
      ensureStarted();
      Worker worker = workerFactory().newWorker(descriptor.taskQueue());
      worker.registerWorkflowImplementationTypes(descriptor.temporalImplementation());
    }
  }

  private GeneratedClassDescriptor resolveProcess(String code) {
    return GlobalManager.globalManager().findGeneratedProcess(code)
            .orElseThrow(() -> new IllegalArgumentException("No generated DSL process: " + code));
  }

  private Object prepareInput(Object input, Class<?> inputType) {
    if (inputType != null) {
      if (input instanceof MapInput mapInput) {
        return DslConfig.dslConfig().mapInputConverter().convert(mapInput.values(), inputType);
      }
      if (input instanceof Map<?, ?> map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = (Map<String, Object>) map;
        return DslConfig.dslConfig().mapInputConverter().convert(parameters, inputType);
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
