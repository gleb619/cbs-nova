package cbs.nova.dsl.builder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.exception.BuilderBusyException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BuilderWorkQueueTest {

  private BuilderWorkQueue queue(int capacity, int workers) {
    var properties = new DslBuilderProperties(
            Path.of(System.getProperty("java.io.tmpdir"), "dsl-builder-queue-test"),
            Duration.ofMinutes(10),
            Duration.ofHours(1),
            null,
            "0.0.1-SNAPSHOT",
            "1.27.0",
            "4.0.4",
            "v1",
            List.of("clean", "build"),
            List.of("dsl", "models"),
            "project/templates",
            null,
            new DslBuilderProperties.Queue(capacity, workers),
            null,
            null,
            null,
            null);
    var queue = new BuilderWorkQueue(properties);
    queue.start();
    return queue;
  }

  @Test
  void executesSubmittedTaskAndReturnsResult() {
    var queue = queue(10, 2);

    String result = queue.submit(() -> "done");

    assertThat(result).isEqualTo("done");
  }

  @Test
  void propagatesTaskFailures() {
    var queue = queue(10, 1);

    assertThatThrownBy(() -> queue.submit(() -> {
      throw new IllegalArgumentException("boom");
    })).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("boom");
  }

  @Test
  void runsTasksOnWorkerThreads() {
    var queue = queue(10, 1);
    var threadNames = new java.util.concurrent.ConcurrentLinkedQueue<String>();

    queue.submit(() -> {
      threadNames.add(Thread.currentThread().getName());
      return null;
    });

    assertThat(threadNames).anyMatch(name -> name.startsWith("dsl-builder-worker-"));
  }

  @Test
  void rejectsSubmissionWhenQueueIsFull() throws Exception {
    var queue = queue(1, 1);
    var blocker = new CountDownLatch(1);
    var started = new CountDownLatch(1);

    var first = new Thread(() -> queue.submit(() -> {
      started.countDown();
      return blocker.await(30, TimeUnit.SECONDS);
    }));
    first.start();
    started.await(5, TimeUnit.SECONDS);

    var queued = new Thread(() -> queue.submit(() -> null));
    queued.start();
    awaitQueueOccupied();

    assertThatThrownBy(() -> queue.submit(() -> null))
            .isInstanceOf(BuilderBusyException.class);

    blocker.countDown();
    first.join(5000);
    queued.join(5000);
  }

  @Test
  void boundsConcurrentExecutionToWorkerCount() throws Exception {
    var queue = queue(10, 2);
    var running = new AtomicInteger();
    var maxRunning = new AtomicInteger();

    var threads = new java.util.ArrayList<Thread>();
    for (int i = 0; i < 6; i++) {
      var thread = new Thread(() -> queue.submit(() -> {
        int current = running.incrementAndGet();
        maxRunning.accumulateAndGet(current, Math::max);
        Thread.sleep(50);
        running.decrementAndGet();
        return null;
      }));
      thread.start();
      threads.add(thread);
    }
    for (Thread thread : threads) {
      thread.join(10000);
    }

    assertThat(maxRunning.get()).isLessThanOrEqualTo(2);
  }

  private void awaitQueueOccupied() throws InterruptedException {
    Thread.sleep(200);
  }
}
