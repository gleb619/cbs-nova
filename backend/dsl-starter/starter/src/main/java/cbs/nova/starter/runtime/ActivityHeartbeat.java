package cbs.nova.starter.runtime;

import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.NonNull;

/**
 * T750: schedules periodic {@code Activity.getExecutionContext().heartbeat(null)} calls around a
 * transaction body so that Temporal does not cancel a long-running activity whose
 * {@code heartbeatTimeout} has been declared in the DSL.
 *
 * <p>
 * Generated activity implementations wrap their {@code GlobalManager.runTransaction(...)} call
 * between {@link #start(Duration)} and {@link #stop(ActivityHeartbeat)} so DSL authors do not have
 * to scatter {@code Activity.getExecutionContext().heartbeat()} calls through their transaction
 * logic. The heartbeat interval is {@code heartbeatTimeout / 3} so a single skipped heartbeat still
 * leaves Temporal two more chances before it cancels the activity — see Temporal docs, "Activity
 * Heartbeating".
 *
 * <p>
 * When invoked outside an activity context (e.g. local invocations that bypass Temporal), the
 * heartbeat callback swallows the resulting {@link IllegalStateException} so local execution keeps
 * working. The returned {@link HeartbeatHandle} is always non-null; pass it to
 * {@link #stop(HeartbeatHandle)} from the {@code finally} block that wraps the transaction body.
 *
 * <p>
 * The {@code ActivityExecutionContext} is captured on the calling thread (the Temporal worker
 * thread that invokes {@code execute}) at {@link #start(Duration)} time and reused on the heartbeat
 * executor thread, because Temporal's context is a thread-local and would otherwise be invisible
 * from a different thread.
 */
public final class ActivityHeartbeat {

  private ActivityHeartbeat() {
  }

  /**
   * Schedule a periodic heartbeat at {@code heartbeatTimeout / 3}. The first heartbeat fires after
   * one period so the caller has a chance to do useful work first.
   *
   * @param heartbeatTimeout
   *          the heartbeat timeout declared in the DSL; must be {@code > 0}
   * @return a non-null {@link HeartbeatHandle} whose {@link HeartbeatHandle#stop()} must be called
   *         from a {@code finally} block to cancel the scheduler and shut down the executor
   */
  public static @NonNull HeartbeatHandle start(@NonNull Duration heartbeatTimeout) {
    if (heartbeatTimeout.isZero() || heartbeatTimeout.isNegative()) {
      throw new IllegalArgumentException("heartbeatTimeout must be > 0");
    }
    long periodMs = heartbeatTimeout.toMillis() / 3;
    if (periodMs < 1) {
      periodMs = 1;
    }
    ThreadFactory factory = new HeartbeatThreadFactory();
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(factory);
    ActivityExecutionContext captured = captureContext();
    ScheduledFuture<?> future = executor.scheduleAtFixedRate(
            () -> heartbeatSafe(captured), periodMs, periodMs, TimeUnit.MILLISECONDS);
    return new HeartbeatHandle(future, executor);
  }

  /**
   * Variant that reuses a single executor across heartbeats. Useful for tests and for callers that
   * already manage their own executor.
   */
  public static @NonNull HeartbeatHandle start(
          @NonNull Duration heartbeatTimeout, @NonNull ScheduledExecutorService executor) {
    if (heartbeatTimeout.isZero() || heartbeatTimeout.isNegative()) {
      throw new IllegalArgumentException("heartbeatTimeout must be > 0");
    }
    long periodMs = heartbeatTimeout.toMillis() / 3;
    if (periodMs < 1) {
      periodMs = 1;
    }
    ActivityExecutionContext captured = captureContext();
    ScheduledFuture<?> future = executor.scheduleAtFixedRate(
            () -> heartbeatSafe(captured), periodMs, periodMs, TimeUnit.MILLISECONDS);
    return new HeartbeatHandle(future, null);
  }

  private static ActivityExecutionContext captureContext() {
    try {
      return Activity.getExecutionContext();
    } catch (IllegalStateException notInActivity) {
      // Local execution (no Temporal activity context). Capture null so the
      // heartbeat callback can short-circuit cleanly — see heartbeatSafe.
      return null;
    }
  }

  private static void heartbeatSafe(ActivityExecutionContext captured) {
    if (captured == null) {
      // start() was called outside an activity context; nothing to heartbeat against.
      return;
    }
    try {
      captured.heartbeat(null);
    } catch (IllegalStateException notInActivity) {
      // Activity context was torn down between start() and the callback — swallow.
    } catch (Throwable t) {
      // Heartbeats must never abort the activity on their own — the next heartbeat
      // (or the eventual worker shutdown) will surface real errors.
    }
  }

  /**
   * Cancel the heartbeat task. Always safe to call, even when the heartbeat was never scheduled
   * (e.g. for a transaction whose DSL did not declare a heartbeat timeout).
   */
  public static void stop(@NonNull HeartbeatHandle handle) {
    if (handle == null) {
      return;
    }
    ScheduledFuture<?> future = handle.future();
    if (future != null) {
      future.cancel(false);
    }
    ScheduledExecutorService executor = handle.executor();
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  /** Pair returned by {@link #start(Duration)}. */
  public static final class HeartbeatHandle {
    private final ScheduledFuture<?> future;
    private final ScheduledExecutorService executor;

    HeartbeatHandle(ScheduledFuture<?> future, ScheduledExecutorService executor) {
      this.future = future;
      this.executor = executor;
    }

    ScheduledFuture<?> future() {
      return future;
    }

    ScheduledExecutorService executor() {
      return executor;
    }
  }

  private static final class HeartbeatThreadFactory implements ThreadFactory {
    private final AtomicLong counter = new AtomicLong();

    @Override
    public Thread newThread(Runnable r) {
      Thread t = new Thread(r, "cbs-nova-heartbeat-" + counter.incrementAndGet());
      t.setDaemon(true);
      return t;
    }
  }
}
