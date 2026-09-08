package cbs.nova.starter.builder;

import cbs.nova.starter.exception.BuilderClientBusyException;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
//TODO: replace with a Resilience4j
public class BuilderRequestQueue {

  private record QueuedTask<T>(Callable<T> call, CompletableFuture<T> future) {
  }

  private final BlockingQueue<QueuedTask<?>> queue;
  private final long offerTimeoutMillis;
  private final List<Thread> workers;
  private volatile boolean accepting = true;

  //TODO: move config to a spring configuration class
  public BuilderRequestQueue(int capacity, long offerTimeoutMillis, int workers) {
    this.queue = new ArrayBlockingQueue<>(Math.max(1, capacity));
    this.offerTimeoutMillis = offerTimeoutMillis;
    this.workers = new ArrayList<>();
    for (int i = 0; i < Math.max(1, workers); i++) {
      Thread thread = new Thread(this::drainLoop, "dsl-builder-client-" + i);
      thread.setDaemon(true);
      this.workers.add(thread);
      thread.start();
    }
  }

  public <T> CompletableFuture<T> submit(Callable<T> call) {
    if (!accepting) {
      throw new IllegalStateException("builder request queue is shut down");
    }
    var future = new CompletableFuture<T>();
    try {
      if (!queue.offer(new QueuedTask<>(call, future), offerTimeoutMillis,
              TimeUnit.MILLISECONDS)) {
        throw new BuilderClientBusyException("builder request queue is full");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BuilderClientBusyException("interrupted while offering builder request");
    }
    return future;
  }

  @PreDestroy
  public void shutdown() {
    accepting = false;
    for (int i = 0; i < workers.size(); i++) {
      queue.offer(new QueuedTask<>(null, null));
    }
    for (Thread worker : workers) {
      worker.interrupt();
      try {
        worker.join(TimeUnit.SECONDS.toMillis(5));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private void drainLoop() {
    while (true) {
      QueuedTask<?> task;
      try {
        task = queue.take();
      } catch (InterruptedException e) {
        return;
      }
      if (task.call() == null) {
        return;
      }
      run(task);
    }
  }

  @SuppressWarnings("unchecked")
  private <T> void run(QueuedTask<?> task) {
    var typed = (QueuedTask<T>) task;
    try {
      typed.future().complete(typed.call().call());
    } catch (Throwable t) {
      typed.future().completeExceptionally(t);
    }
  }

}
