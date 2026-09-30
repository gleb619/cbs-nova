package cbs.nova.starter.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.starter.runtime.ActivityHeartbeat.HeartbeatHandle;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T750 unit tests for the heartbeat helper that wraps Temporal-generated activities.
 *
 * <p>
 * The public {@code io.temporal.activity.Activity#getExecutionContext()} API has no public setter,
 * so the thread-local that backs it lives behind a package-private
 * {@code CurrentActivityExecutionContext}. To exercise the heartbeat path without standing up a
 * Temporal worker we reach into that thread-local via reflection; the alternative (a Temporal
 * {@code TestWorkflowEnvironment}) costs seconds per test and is not worth it for this helper.
 */
class ActivityHeartbeatTest {

  private static final Field CURRENT_FIELD;

  static {
    try {
      Class<?> holder = Class.forName(
              "io.temporal.internal.activity.CurrentActivityExecutionContext");
      Field f = holder.getDeclaredField("CURRENT");
      f.setAccessible(true);
      CURRENT_FIELD = f;
    } catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static final CountDownLatch HEARTBEAT_LATCH = new CountDownLatch(2);

  private Object previous;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void rememberContext() throws IllegalAccessException {
    ThreadLocal<Object> tl = (ThreadLocal<Object>) CURRENT_FIELD.get(null);
    previous = tl.get();
  }

  @AfterEach
  @SuppressWarnings("unchecked")
  void restoreContext() throws IllegalAccessException {
    ThreadLocal<Object> tl = (ThreadLocal<Object>) CURRENT_FIELD.get(null);
    if (previous != null) {
      tl.set(previous);
    } else {
      tl.remove();
    }
  }

  @Test
  void startSchedulesPeriodicTaskAndStopCancelsIt() throws Exception {
    // Reflectively install an ActivityExecutionContext proxy that counts invocations.
    // The proxy is captured by ActivityHeartbeat.start() on the test thread and
    // reused on the executor thread, so the periodic heartbeat actually fires.
    java.lang.reflect.InvocationHandler counter = (proxy, method, args) -> {
      if ("heartbeat".equals(method.getName())) {
        HEARTBEAT_LATCH.countDown();
      }
      return null;
    };
    Object ctx = java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{Class.forName("io.temporal.activity.ActivityExecutionContext")},
            counter);
    ((ThreadLocal<Object>) CURRENT_FIELD.get(null)).set(ctx);

    HeartbeatHandle handle = ActivityHeartbeat.start(Duration.ofMillis(30));
    try {
      boolean fired = HEARTBEAT_LATCH.await(2, TimeUnit.SECONDS);
      ScheduledFuture<?> f = handle.future();
      assertThat(Boolean.valueOf(fired))
              .as("periodic heartbeat should fire at heartbeatTimeout/3")
              .isTrue();
      assertThat((Object) f).isNotNull();
    } finally {
      ActivityHeartbeat.stop(handle);
    }
    ScheduledFuture<?> future = handle.future();
    assertThat(future.isCancelled() || future.isDone()).isTrue();
  }

  @Test
  void startReusesProvidedExecutor() {
    ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "hb-test-reuse");
      t.setDaemon(true);
      return t;
    });
    try {
      HeartbeatHandle handle = ActivityHeartbeat.start(Duration.ofMillis(100), exec);
      assertThat(handle.executor()).isNull();
      ActivityHeartbeat.stop(handle);
    } finally {
      exec.shutdownNow();
    }
  }

  @Test
  void stopIsNoopWhenCalledOnLiveHandle() {
    // Stop on a freshly-started handle must cancel + shut down its executor without
    // throwing — generated code wraps the transaction body in a finally block and a
    // misbehaving stop helper would mask real activity failures.
    HeartbeatHandle handle = ActivityHeartbeat.start(Duration.ofMillis(100));
    ScheduledExecutorService executor = handle.executor();
    ActivityHeartbeat.stop(handle);
    assertThat(executor.isShutdown()).isTrue();
  }

  @Test
  void startRejectsNonPositiveTimeout() {
    assertThatThrownBy(() -> ActivityHeartbeat.start(Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ActivityHeartbeat.start(Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class);
  }
}
