package cbs.nova.starter.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadConfig;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Resilience4j thread-pool bulkhead (request queue) configuration used by
 * {@link DslBuilderClient}.
 *
 * <p>
 * T498 replaced the hand-rolled bounded queue + worker pool. The new implementation rejects
 * immediately when the bounded queue is full, instead of waiting up to {@code offerTimeoutMillis}.
 */
class BuilderRequestQueueTest {

  private final AtomicReference<ThreadPoolBulkhead> queue = new AtomicReference<>();

  @AfterEach
  void tearDown() {
    if (queue.get() != null) {
      try {
        queue.get().close();
      } catch (Exception ignored) {
      }
    }
  }

  @Test
  void executesSubmittedTask() throws Exception {
    var requestQueue = track(queue(4, 1));

    var future = requestQueue.submit(() -> "done").toCompletableFuture();

    assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("done");
  }

  @Test
  void propagatesTaskFailureToFuture() {
    var requestQueue = track(queue(4, 1));

    var future = requestQueue.submit(() -> {
      throw new IllegalStateException("boom");
    }).toCompletableFuture();

    assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
            .rootCause().isInstanceOf(IllegalStateException.class).hasMessage("boom");
  }

  @Test
  void fullQueueRejectsImmediately() throws Exception {
    var requestQueue = track(queue(1, 1));
    var started = new CountDownLatch(1);
    var blocker = new CountDownLatch(1);
    requestQueue.submit(() -> {
      started.countDown();
      blocker.await(10, TimeUnit.SECONDS);
      return "first";
    });
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
    requestQueue.submit(() -> "queued");

    long start = System.nanoTime();
    assertThatThrownBy(() -> requestQueue.submit(() -> "overflow"))
            .isInstanceOf(BulkheadFullException.class);
    // Resilience4j ThreadPoolBulkhead rejects immediately when the bounded queue is full; the old
    // hand-rolled queue waited up to offerTimeoutMillis before throwing.
    assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(100);
    blocker.countDown();
  }

  @Test
  void shutdownRejectsNewSubmissions() {
    var requestQueue = track(queue(4, 1));
    try {
      requestQueue.close();
    } catch (Exception ignored) {
    }
    queue.set(null);

    assertThatThrownBy(() -> requestQueue.submit(() -> "late"))
            .isInstanceOfAny(BulkheadFullException.class, RejectedExecutionException.class);
  }

  @Test
  void shutdownCompletesAlreadyQueuedTasks() throws Exception {
    var requestQueue = track(queue(4, 1));
    var future = requestQueue.submit(() -> "queued-before-shutdown").toCompletableFuture();

    try {
      requestQueue.close();
    } catch (Exception ignored) {
    }
    queue.set(null);

    assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("queued-before-shutdown");
  }

  private static ThreadPoolBulkhead queue(int capacity, int workers) {
    return ThreadPoolBulkhead.of("test-queue", ThreadPoolBulkheadConfig.custom()
            .maxThreadPoolSize(Math.max(1, workers))
            .coreThreadPoolSize(Math.max(1, workers))
            .queueCapacity(Math.max(1, capacity))
            .build());
  }

  private ThreadPoolBulkhead track(ThreadPoolBulkhead requestQueue) {
    queue.set(requestQueue);
    return requestQueue;
  }

}
