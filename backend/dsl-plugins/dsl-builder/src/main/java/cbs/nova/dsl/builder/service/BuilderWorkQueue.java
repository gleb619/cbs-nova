package cbs.nova.dsl.builder.service;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.exception.BuilderBusyException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BuilderWorkQueue {

  private final LinkedBlockingQueue<Runnable> queue;
  private final List<Thread> workers;

  // TODO: move to a configuration. replace with lomboks contructor
  public BuilderWorkQueue(DslBuilderProperties properties) {
    this.queue = new LinkedBlockingQueue<>(Math.max(1, properties.queue().capacity()));
    int workerCount = Math.max(1, properties.queue().workers());
    this.workers = new ArrayList<>(workerCount);
    for (int i = 0; i < workerCount; i++) {
      Thread worker = new Thread(this::drain, "dsl-builder-worker-" + i);
      worker.setDaemon(true);
      workers.add(worker);
    }
  }

  @PostConstruct
  void start() {
    workers.forEach(Thread::start);
  }

  @PreDestroy
  void stop() {
    workers.forEach(Thread::interrupt);
  }

  public <T> T submit(Callable<T> task) {
    FutureTask<T> future = new FutureTask<>(task);
    if (!queue.offer(future)) {
      throw new BuilderBusyException("builder work queue is full");
    }
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while waiting for builder task", e);
    } catch (ExecutionException e) {
      return rethrow(e.getCause());
    }
  }

  @SuppressWarnings("unchecked")
  private <T> T rethrow(Throwable cause) {
    if (cause instanceof RuntimeException runtime) {
      throw runtime;
    }
    if (cause instanceof Error error) {
      throw error;
    }
    throw new IllegalStateException(cause);
  }

  private void drain() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        queue.take().run();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (RuntimeException e) {
        log.warn("builder work task failed: {}", e.getMessage());
      }
    }
  }
}
