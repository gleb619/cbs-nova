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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BuilderWorkQueue {

  private final DslBuilderProperties properties;
  private LinkedBlockingQueue<Runnable> queue;
  private List<Thread> workers;

  @PostConstruct
  void start() {
    if (queue == null) {
      queue = new LinkedBlockingQueue<>(Math.max(1, properties.queue().capacity()));
    }
    if (workers == null) {
      int workerCount = Math.max(1, properties.queue().workers());
      workers = new ArrayList<>(workerCount);
      for (int i = 0; i < workerCount; i++) {
        Thread worker = new Thread(this::drain, "dsl-builder-worker-" + i);
        worker.setDaemon(true);
        workers.add(worker);
      }
    }
    workers.forEach(Thread::start);
  }

  @PreDestroy
  void stop() {
    if (workers != null) {
      workers.forEach(Thread::interrupt);
    }
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
