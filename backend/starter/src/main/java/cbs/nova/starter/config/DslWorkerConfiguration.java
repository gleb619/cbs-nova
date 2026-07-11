package cbs.nova.starter.config;

import io.temporal.client.WorkflowClient;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(name = "dsl.worker.enabled", havingValue = "true")
public class DslWorkerConfiguration {

  @Value("${dsl.task-queue:dsl-task-queue}")
  private String taskQueue;

  @Bean
  Worker dslWorker(WorkflowClient workflowClient) {
    WorkerFactory factory = WorkerFactory.newInstance(workflowClient);
    Worker worker = factory.newWorker(taskQueue);
    // Generated workflow and activity classes are registered here when available.
    // Classpath scanning for cbs.nova.dsl.generated.* impls can be wired in when
    // dsl-examples generates and exposes those classes on the runtime classpath.
    factory.start();
    return worker;
  }
}
