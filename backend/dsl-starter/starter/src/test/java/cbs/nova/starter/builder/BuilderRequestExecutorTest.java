package cbs.nova.starter.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.starter.exception.BuilderClientBusyException;
import cbs.nova.starter.exception.BuilderUnavailableException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

/**
 * Unit spec for {@link BuilderRequestExecutor}. Pins the failure-to-domain-exception mapping
 * enforced by the Resilience4j wrapper stack (ThreadPoolBulkhead → Bulkhead → CircuitBreaker).
 */
class BuilderRequestExecutorTest {

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
  void executeReturnsValueOnSuccess() throws Exception {
    var executor = executor(minimalBreaker());

    assertThat(executor.execute(() -> "ok")).isEqualTo("ok");
  }

  @Test
  void runtimeExceptionFromCallPropagatesUnchanged() {
    var executor = executor(minimalBreaker());

    var thrown = new IllegalArgumentException("bad request");
    assertThatThrownBy(() -> executor.execute(() -> {
      throw thrown;
    })).isSameAs(thrown);
  }

  @Test
  void checkedExceptionBecomesIllegalStateExceptionWithCause() {
    var executor = executor(minimalBreaker());

    var thrown = new IOException("io down");
    assertThatThrownBy(() -> executor.execute(() -> {
      throw thrown;
    })).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("builder call failed")
            .hasMessageContaining("io down")
            .hasCause(thrown);
  }

  @Test
  void errorIsRethrownUnchanged() {
    var executor = executor(minimalBreaker());

    var thrown = new OutOfMemoryError("oom");
    assertThatThrownBy(() -> executor.execute(() -> {
      throw thrown;
    })).isSameAs(thrown);
  }

  @Test
  void resourceAccessExceptionMapsToBuilderUnavailableWithCause() {
    var executor = executor(minimalBreaker());

    var cause = new ResourceAccessException("connection refused");
    assertThatThrownBy(() -> executor.execute(() -> {
      throw cause;
    })).isInstanceOf(BuilderUnavailableException.class)
            .hasMessageContaining("connection refused")
            .hasCause(cause);
  }

  @Test
  void openBreakerMapsToBuilderUnavailable() {
    var breaker = minimalBreaker();
    var executor = executor(breaker);

    var cause = new ResourceAccessException("first failure");
    assertThatThrownBy(() -> executor.execute(() -> {
      throw cause;
    })).isInstanceOf(BuilderUnavailableException.class);
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

    assertThatThrownBy(() -> executor.execute(() -> "never called"))
            .isInstanceOf(BuilderUnavailableException.class)
            .hasMessage("DSL builder circuit breaker is open");
  }

  @Test
  void fullQueueMapsToBuilderClientBusy() throws Exception {
    var requestQueue = track(queue(1, 1));
    var executor = executor(requestQueue, minimalBulkhead(), minimalBreaker());

    var started = new CountDownLatch(1);
    var blocker = new CountDownLatch(1);
    requestQueue.submit(() -> {
      started.countDown();
      try {
        blocker.await(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
      return "first";
    });
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
    // Fill the queue (capacity 1) so the next submission overflows.
    requestQueue.submit(() -> "queued");

    assertThatThrownBy(() -> executor.execute(() -> "overflow"))
            .isInstanceOf(BuilderClientBusyException.class)
            .hasMessageContaining("queue is full");

    blocker.countDown();
  }

  @Test
  void saturatedBulkheadMapsToBuilderClientBusy() {
    var bulkhead = Bulkhead.of("saturated", BulkheadConfig.custom()
            .maxConcurrentCalls(1)
            .maxWaitDuration(Duration.ZERO)
            .build());
    bulkhead.acquirePermission();
    try {
      var executor = executor(bulkhead);

      assertThatThrownBy(() -> executor.execute(() -> "never"))
              .isInstanceOf(BuilderClientBusyException.class)
              .hasMessage("builder client bulkhead saturated");
    } finally {
      bulkhead.releasePermission();
    }
  }

  @Test
  void interruptWhileWaitingRestoresFlagAndThrowsIllegalState() throws Exception {
    var requestQueue = track(queue(1, 1));
    var started = new CountDownLatch(1);
    var blocker = new CountDownLatch(1);
    var executor = executor(requestQueue, minimalBulkhead(), minimalBreaker());

    // Park the only worker so the next submit must wait in the queue, forcing execute() into
    // Future.get().
    var blockerFuture = requestQueue.submit(() -> {
      started.countDown();
      try {
        blocker.await(60, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
      return "blocker done";
    }).toCompletableFuture();
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

    var capture = new AtomicReference<Throwable>();
    var interruptFlag = new AtomicBoolean();
    var caller = new Thread(() -> {
      try {
        executor.execute(() -> "never");
        capture.set(new AssertionError("expected exception"));
      } catch (Throwable t) {
        capture.set(t);
        interruptFlag.set(Thread.currentThread().isInterrupted());
      }
    }, "t881-interrupt-caller");
    caller.start();

    // Wait for caller to reach Future.get() then interrupt it.
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (caller.isAlive() && caller.getState() != Thread.State.WAITING
            && System.nanoTime() < deadline) {
      Thread.sleep(10);
    }
    caller.interrupt();
    caller.join(5000);

    assertThat(capture.get()).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("interrupted");
    assertThat(interruptFlag.get()).isTrue();

    blocker.countDown();
    assertThat(blockerFuture.get(5, TimeUnit.SECONDS)).isEqualTo("blocker done");
  }

  private BuilderRequestExecutor executor(CircuitBreaker breaker) {
    return executor(track(queue(1, 1)), minimalBulkhead(), breaker);
  }

  private BuilderRequestExecutor executor(Bulkhead bulkhead) {
    return executor(track(queue(1, 1)), bulkhead, minimalBreaker());
  }

  private BuilderRequestExecutor executor(ThreadPoolBulkhead requestQueue, Bulkhead bulkhead,
          CircuitBreaker breaker) {
    return new BuilderRequestExecutor(requestQueue, bulkhead, breaker);
  }

  private static ThreadPoolBulkhead queue(int capacity, int workers) {
    return ThreadPoolBulkhead.of("test-queue", ThreadPoolBulkheadConfig.custom()
            .maxThreadPoolSize(Math.max(1, workers))
            .coreThreadPoolSize(Math.max(1, workers))
            .queueCapacity(Math.max(1, capacity))
            .build());
  }

  private static Bulkhead minimalBulkhead() {
    return Bulkhead.of("test-bulkhead", BulkheadConfig.custom()
            .maxConcurrentCalls(1)
            .maxWaitDuration(Duration.ofSeconds(5))
            .build());
  }

  private static CircuitBreaker minimalBreaker() {
    return CircuitBreaker.of("test-breaker", CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(1)
            .minimumNumberOfCalls(1)
            .failureRateThreshold(100f)
            .waitDurationInOpenState(Duration.ofMinutes(1))
            .recordException(e -> e instanceof BuilderUnavailableException)
            .ignoreException(e -> !(e instanceof BuilderUnavailableException))
            .build());
  }

  private ThreadPoolBulkhead track(ThreadPoolBulkhead requestQueue) {
    queue.set(requestQueue);
    return requestQueue;
  }

}
