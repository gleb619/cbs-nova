package cbs.nova.starter.config;

import io.temporal.client.WorkflowClient;
import io.temporal.worker.TypeAlreadyRegisteredException;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.workflow.WorkflowInterface;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.util.ClassUtils;

@AutoConfiguration
@ConditionalOnProperty(name = "dsl.worker.enabled", havingValue = "true")
public class DslWorkerConfiguration {

  @Value("${dsl.task-queue:dsl-task-queue}")
  private String taskQueue;

  @Bean
  Worker dslWorker(WorkflowClient workflowClient) {
    WorkerFactory factory = WorkerFactory.newInstance(workflowClient);
    Worker worker = factory.newWorker(taskQueue);
    registerGeneratedImplementations(worker);
    factory.start();
    return worker;
  }

  private void registerGeneratedImplementations(Worker worker) {
    var resolver = new PathMatchingResourcePatternResolver();
    var readerFactory = new CachingMetadataReaderFactory();
    String packageSearchPath = "classpath*:cbs/nova/dsl/generated/**/*.class";
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
}
