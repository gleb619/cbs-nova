package cbs.app.temporal;

import io.temporal.worker.WorkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class TemporalWorkerRegistrar implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(TemporalWorkerRegistrar.class);
  private final WorkerFactory workerFactory;
  @Value("${temporal.task-queue:WORKFLOW_TASK_QUEUE}")
  private String taskQueue;

  public TemporalWorkerRegistrar(WorkerFactory workerFactory) {
    this.workerFactory = workerFactory;
  }

  @Override
  public void run(ApplicationArguments args) {
    workerFactory.newWorker(taskQueue);
    workerFactory.start();
    LOG.info("Started Temporal worker on task queue: {}", taskQueue);
  }
}
