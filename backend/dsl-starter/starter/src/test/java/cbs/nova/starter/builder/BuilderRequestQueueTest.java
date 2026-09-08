package cbs.nova.starter.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.starter.exception.BuilderClientBusyException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BuilderRequestQueueTest {

  private final AtomicReference<BuilderRequestQueue> queue = new AtomicReference<>();

  @AfterEach
  void tearDown() {
    if (queue.get() != null) {
      queue.get().shutdown();
    }
  }

  @Test
  void executesSubmittedTask() throws Exception {
    var requestQueue = track(new BuilderRequestQueue(4, 5000, 1));

    var future = requestQueue.submit(() -> "done");

    assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("done");
  }

  @Test
  void propagatesTaskFailureToFuture() {
    var requestQueue = track(new BuilderRequestQueue(4, 5000, 1));

    var future = requestQueue.submit(() -> {
      throw new IllegalStateException("boom");
    });

    assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
            .rootCause().isInstanceOf(IllegalStateException.class).hasMessage("boom");
  }

  @Test
  void fullQueueRejectsWithBusyException() throws Exception {
    var requestQueue = track(new BuilderRequestQueue(1, 200, 1));
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
            .isInstanceOf(BuilderClientBusyException.class);
    assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))
            .isGreaterThanOrEqualTo(150);
    blocker.countDown();
  }

  @Test
  void shutdownRejectsNewSubmissions() {
    var requestQueue = track(new BuilderRequestQueue(4, 5000, 1));
    requestQueue.shutdown();
    queue.set(null);

    assertThatThrownBy(() -> requestQueue.submit(() -> "late"))
            .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shutdownCompletesAlreadyQueuedTasks() throws Exception {
    var requestQueue = track(new BuilderRequestQueue(4, 5000, 1));
    var future = requestQueue.submit(() -> "queued-before-shutdown");

    requestQueue.shutdown();
    queue.set(null);

    assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("queued-before-shutdown");
  }

  private BuilderRequestQueue track(BuilderRequestQueue requestQueue) {
    queue.set(requestQueue);
    return requestQueue;
  }

}
