package cbs.nova.starter.services;

import cbs.nova.dsl.GeneratedClassDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.converter.MapInputConverter;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Executes generated DSL workflows by their string code, hiding Temporal worker and stub wiring.
 */
@Service
@RequiredArgsConstructor
public class TemporalDslService {

  private final WorkflowClient workflowClient;

  /**
   * Executes a generated DSL process with an already typed input.
   */
  public <O> O execute(
          @NonNull String code, @NonNull Object input, @NonNull Class<O> outputType) {
    return execute(code, input, outputType, null);
  }

  /**
   * Executes a generated DSL process, optionally overriding default {@link WorkflowOptions}.
   */
  public <O> O execute(
          @NonNull String code,
          @NonNull Object input,
          @NonNull Class<O> outputType,
          WorkflowOptions options) {
    GeneratedClassDescriptor descriptor = resolveProcess(code);
    WorkflowOptions effectiveOptions = options != null
            ? options
            : defaultOptions(descriptor);

    WorkerFactory workerFactory = WorkerFactory.newInstance(workflowClient);
    try {
      Worker worker = workerFactory.newWorker(descriptor.taskQueue());
      worker.registerWorkflowImplementationTypes(descriptor.temporalImplementation());
      workerFactory.start();

      Object preparedInput = prepareInput(input, descriptor.inputType());
      Object stub = workflowClient.newWorkflowStub(descriptor.temporalInterface(),
              effectiveOptions);
      Method runMethod = findRunMethod(descriptor.temporalInterface());
      Object result = runMethod.invoke(stub, preparedInput);
      return outputType.cast(result);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      throw new RuntimeException(
              "DSL workflow " + code + " failed: " + cause.getMessage(), cause);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("Failed to execute DSL workflow " + code, e);
    } finally {
      workerFactory.shutdown();
    }
  }

  /**
   * Convenience overload for parameter-map inputs. The map is converted to the process input type.
   */
  public <O> O execute(
          @NonNull String code,
          @NonNull Map<String, Object> parameters,
          @NonNull Class<O> outputType) {
    return execute(code, (Object) parameters, outputType);
  }

  private GeneratedClassDescriptor resolveProcess(String code) {
    return GlobalManager.getInstance().findGeneratedProcess(code)
            .orElseThrow(() -> new IllegalArgumentException("No generated DSL process: " + code));
  }

  private Object prepareInput(Object input, Class<?> inputType) {
    if (inputType != null && input instanceof Map<?, ?> map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> parameters = (Map<String, Object>) map;
      return MapInputConverter.convert(parameters, inputType);
    }
    return input;
  }

  private Method findRunMethod(Class<?> workflowInterface) {
    for (Method method : workflowInterface.getMethods()) {
      if ("run".equals(method.getName()) && method.getParameterCount() == 1) {
        return method;
      }
    }
    throw new IllegalArgumentException(
            "No single-arg run method on " + workflowInterface.getName());
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
