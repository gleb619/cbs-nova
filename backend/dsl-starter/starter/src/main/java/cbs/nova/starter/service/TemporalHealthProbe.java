package cbs.nova.starter.service;

import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.temporal.serviceclient.WorkflowServiceStubs;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Lightweight reachability probe for the Temporal {@code WorkflowServiceStubs} used by the starter.
 *
 * <p>
 * Performs a gRPC {@code Health.Check} call against the configured Temporal server, bounded by a
 * short timeout. Returns a {@link TemporalHealth} value object describing the outcome; never throws
 * out of {@link #probe()}.
 */
@Component
public class TemporalHealthProbe {

  private final WorkflowServiceStubs stubs;
  private final Duration timeout;
  private final ExecutorService executor;

  public TemporalHealthProbe(WorkflowServiceStubs stubs, Duration timeout) {
    this(stubs, timeout, Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "cbs-nova-temporal-health-probe");
      t.setDaemon(true);
      return t;
    }));
  }

  TemporalHealthProbe(WorkflowServiceStubs stubs, Duration timeout, ExecutorService executor) {
    this.stubs = stubs;
    this.timeout = timeout;
    this.executor = executor;
  }

  public TemporalHealth probe() {
    String target = resolveTarget();
    Callable<HealthCheckResponse> call = () -> stubs.healthCheck();
    Future<HealthCheckResponse> future = executor.submit(call);
    try {
      HealthCheckResponse response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (response.getStatus() == ServingStatus.SERVING) {
        return TemporalHealth.reachable(target);
      }
      return TemporalHealth.unreachable(target,
              "temporal reported " + response.getStatus());
    } catch (TimeoutException e) {
      future.cancel(true);
      return TemporalHealth.unreachable(target, "timeout after " + timeout.toMillis() + "ms");
    } catch (ExecutionException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      return TemporalHealth.unreachable(target, shortMessage(cause));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return TemporalHealth.unreachable(target, "interrupted");
    } catch (RuntimeException e) {
      return TemporalHealth.unreachable(target, shortMessage(e));
    }
  }

  private String resolveTarget() {
    try {
      String t = stubs.getOptions().getTarget();
      return t != null && !t.isBlank() ? t : "unknown";
    } catch (RuntimeException e) {
      return "unknown";
    }
  }

  private static String shortMessage(Throwable t) {
    String msg = t.getMessage();
    return msg != null ? t.getClass().getSimpleName() + ": " + msg : t.getClass().getSimpleName();
  }

  public record TemporalHealth(boolean reachable, String target, @Nullable String error) {

    public static TemporalHealth reachable(String target) {
      return new TemporalHealth(true, target, null);
    }

    public static TemporalHealth unreachable(String target, @Nullable String error) {
      return new TemporalHealth(false, target, error);
    }
  }
}
