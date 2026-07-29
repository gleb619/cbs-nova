package cbs.nova.starter.config;

import io.temporal.client.WorkflowClient;
import io.temporal.worker.TypeAlreadyRegisteredException;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.workflow.WorkflowInterface;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.util.ClassUtils;

import java.util.concurrent.TimeUnit;

@AutoConfiguration
@ConditionalOnProperty(name = "dsl.worker.enabled", havingValue = "true")
public class DslWorkerConfiguration {

  @Value("${dsl.task-queue:dsl-task-queue}")
  private String taskQueue;

  @Bean
  WorkerFactory dslWorkerFactory(WorkflowClient workflowClient) {
    return createWorkerFactory(workflowClient);
  }

  /**
   * Builds the Temporal {@link WorkerFactory}. Exposed as protected so tests can substitute a
   * mocked factory without needing a real Temporal server.
   */
  protected WorkerFactory createWorkerFactory(WorkflowClient workflowClient) {
    return WorkerFactory.newInstance(workflowClient);
  }

  @Bean
  Worker dslWorker(WorkerFactory dslWorkerFactory) {
    Worker worker = dslWorkerFactory.newWorker(taskQueue);
    registerGeneratedImplementations(worker);
    return worker;
  }

  @Bean
  SmartLifecycle dslWorkerFactoryLifecycle(WorkerFactory dslWorkerFactory) {
    return new WorkerFactoryLifecycle(dslWorkerFactory);
  }

  @Deprecated(forRemoval = true)
  private void registerGeneratedImplementations(Worker worker) {
    var resolver = new PathMatchingResourcePatternResolver();
    var readerFactory = new CachingMetadataReaderFactory();
    // Generated implementations may live under the default cbs.nova.dsl.generated tree or under a
    // project-specific package such as cbs.nova.dslexamples. The dsl* wildcard covers both.
    // TODO: use spi declaration instead
    String packageSearchPath = "classpath*:cbs/nova/dsl*/**/*.class";
    try {
      var resources = resolver.getResources(packageSearchPath);
      for (var resource : resources) {
        if (!resource.isReadable()) {
          continue;
        }
        MetadataReader reader = readerFactory.getMetadataReader(resource);
        String className = reader.getClassMetadata().getClassName();
        Class<?> cls = ClassUtils.forName(className,
                Thread.currentThread().getContextClassLoader());
        String simpleName = cls.getSimpleName();

        // TODO: remove reflection, use typed info instead
        if (simpleName.endsWith("ProcessDefinition") && implementsWorkflowInterface(cls)) {
          worker.registerWorkflowImplementationTypes(cls);
        } else if (simpleName.endsWith("TransactionDefinition")) {
          Object instance = cls.getDeclaredConstructor().newInstance();
          try {
            worker.registerActivitiesImplementations(instance);
          } catch (TypeAlreadyRegisteredException ignored) {
            // multiple generated transactions share the default activity method name;
            // the first registration wins, subsequent duplicates are skipped
          }
        }
      }
    } catch (Exception e) {
      throw new IllegalStateException("Failed to scan generated DSL implementations", e);
    }
  }

  private static boolean implementsWorkflowInterface(Class<?> cls) {
    for (Class<?> iface : cls.getInterfaces()) {
      if (iface.isAnnotationPresent(WorkflowInterface.class)) {
        return true;
      }
    }
    return false;
  }

  /**
   * SmartLifecycle adapter that owns the Temporal WorkerFactory start/stop sequence.
   *
   * <p>
   * {@link SmartLifecycle#start()} is invoked after the Spring context is refreshed, kicking off
   * pollers. {@link SmartLifecycle#stop(Runnable)} (and {@link #stop()}) shut the factory down and
   * wait for in-flight workers to terminate before the JVM exits, preventing lost work and
   * abandoned pollers.
   */
  static final class WorkerFactoryLifecycle implements SmartLifecycle {

    /**
     * Bounded wait for workers to terminate after shutdown. Long enough for normal in-flight
     * workflow/activity tasks to drain on shared CI hardware; short enough that a wedged server
     * cannot indefinitely block Spring context shutdown.
     */
    private static final long TERMINATION_AWAIT_SECONDS = 10L;

    private final WorkerFactory factory;
    private volatile boolean running;

    WorkerFactoryLifecycle(WorkerFactory factory) {
      this.factory = factory;
    }

    @Override
    public void start() {
      factory.start();
      running = true;
    }

    @Override
    public void stop() {
      factory.shutdown();
      factory.awaitTermination(TERMINATION_AWAIT_SECONDS, TimeUnit.SECONDS);
      running = false;
    }

    @Override
    public boolean isRunning() {
      return running;
    }

    @Override
    public int getPhase() {
      // Start as late as possible, stop as early as possible — workers must be ready
      // before app traffic begins and must drain before beans that depend on them close.
      return Integer.MAX_VALUE;
    }
  }
}
