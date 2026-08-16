package cbs.nova.starter.config;

import cbs.nova.dsl.GeneratedClassDescriptor;
import cbs.nova.dsl.GeneratedClassProvider;
import cbs.nova.starter.config.properties.DslProperties;
import io.temporal.client.WorkflowClient;
import io.temporal.worker.TypeAlreadyRegisteredException;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;

import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;

@AutoConfiguration
@ConditionalOnProperty(name = "dsl.worker.enabled", havingValue = "true")
@EnableConfigurationProperties(DslProperties.class)
public class DslWorkerConfiguration {

  @Bean
  WorkerFactory dslWorkerFactory(WorkflowClient workflowClient) {
    return createWorkerFactory(workflowClient);
  }

  protected WorkerFactory createWorkerFactory(WorkflowClient workflowClient) {
    return WorkerFactory.newInstance(workflowClient);
  }

  @Bean
  Worker dslWorker(WorkerFactory dslWorkerFactory, DslProperties dslProperties) {
    Worker worker = dslWorkerFactory.newWorker(dslProperties.taskQueue());
    registerGeneratedImplementations(worker);
    return worker;
  }

  @Bean
  SmartLifecycle dslWorkerFactoryLifecycle(WorkerFactory dslWorkerFactory) {
    return new WorkerFactoryLifecycle(dslWorkerFactory);
  }

  private void registerGeneratedImplementations(Worker worker) {
    var classLoader = Thread.currentThread().getContextClassLoader();
    ServiceLoader.load(GeneratedClassProvider.class, classLoader)
            .forEach(provider -> registerDescriptor(worker, provider));
  }

  private void registerDescriptor(Worker worker, GeneratedClassProvider provider) {
    var descriptor = provider.descriptor();
    switch (descriptor.type()) {
      case PROCESS ->
        worker.registerWorkflowImplementationTypes(descriptor.temporalImplementation());
      case TRANSACTION -> {
        try {
          worker.registerActivitiesImplementations(provider.implementationInstance());
        } catch (TypeAlreadyRegisteredException ignored) {
          // multiple generated transactions share the default activity method name;
          // the first registration wins, subsequent duplicates are skipped
        }
      }
      default -> {
        // Functions are not generated as Temporal classes.
      }
    }
  }

  static final class WorkerFactoryLifecycle implements SmartLifecycle {

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
