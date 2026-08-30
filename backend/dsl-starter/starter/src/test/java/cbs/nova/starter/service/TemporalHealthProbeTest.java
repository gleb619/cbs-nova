package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TemporalHealthProbeTest {

  @Test
  void probeReturnsReachableWhenHealthCheckReportsServing() {
    HealthCheckResponse serving = HealthCheckResponse.newBuilder()
            .setStatus(ServingStatus.SERVING)
            .build();
    WorkflowServiceStubs stubs = stub(serving);

    TemporalHealthProbe probe = new TemporalHealthProbe(stubs, Duration.ofSeconds(1));
    TemporalHealthProbe.TemporalHealth health = probe.probe();

    assertThat(health.reachable()).isTrue();
    assertThat(health.target()).isEqualTo("127.0.0.1:7233");
    assertThat(health.error()).isNull();
  }

  @Test
  void probeReturnsUnreachableWhenHealthCheckReportsNotServing() {
    HealthCheckResponse notServing = HealthCheckResponse.newBuilder()
            .setStatus(ServingStatus.NOT_SERVING)
            .build();
    WorkflowServiceStubs stubs = stub(notServing);

    TemporalHealthProbe probe = new TemporalHealthProbe(stubs, Duration.ofSeconds(1));
    TemporalHealthProbe.TemporalHealth health = probe.probe();

    assertThat(health.reachable()).isFalse();
    assertThat(health.target()).isEqualTo("127.0.0.1:7233");
    assertThat(health.error()).contains("NOT_SERVING");
  }

  @Test
  void probeReturnsUnreachableWhenHealthCheckThrows() {
    WorkflowServiceStubs stubs = stubThrowing(new IllegalStateException("boom"));

    TemporalHealthProbe probe = new TemporalHealthProbe(stubs, Duration.ofSeconds(1));
    TemporalHealthProbe.TemporalHealth health = probe.probe();

    assertThat(health.reachable()).isFalse();
    assertThat(health.error()).contains("IllegalStateException").contains("boom");
  }

  @Test
  void probeTimesOutWithoutHangingWhenHealthCheckSleepsTooLong() {
    Duration timeout = Duration.ofMillis(100);
    ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "temporal-probe-test");
      t.setDaemon(true);
      return t;
    });
    try {
      WorkflowServiceStubs stubs = stubSleeping(timeout.toMillis() * 20);
      TemporalHealthProbe probe = new TemporalHealthProbe(stubs, timeout, executor);

      long start = System.nanoTime();
      TemporalHealthProbe.TemporalHealth health = probe.probe();
      long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

      assertThat(health.reachable()).isFalse();
      assertThat(health.error()).contains("timeout");
      assertThat(elapsedMillis)
              .as("probe must return within the configured timeout, not wait for the sleep")
              .isLessThan(timeout.toMillis() * 10);
    } finally {
      executor.shutdownNow();
    }
  }

  private static WorkflowServiceStubs stub(HealthCheckResponse response) {
    WorkflowServiceStubs stubs = mock(WorkflowServiceStubs.class);
    when(stubs.getOptions()).thenReturn(options());
    when(stubs.healthCheck()).thenReturn(response);
    return stubs;
  }

  private static WorkflowServiceStubs stubThrowing(RuntimeException error) {
    WorkflowServiceStubs stubs = mock(WorkflowServiceStubs.class);
    when(stubs.getOptions()).thenReturn(options());
    when(stubs.healthCheck()).thenThrow(error);
    return stubs;
  }

  private static WorkflowServiceStubs stubSleeping(long millis) {
    WorkflowServiceStubs stubs = mock(WorkflowServiceStubs.class);
    when(stubs.getOptions()).thenReturn(options());
    when(stubs.healthCheck()).thenAnswer(invocation -> {
      try {
        Thread.sleep(millis);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
      return HealthCheckResponse.newBuilder()
              .setStatus(ServingStatus.SERVING)
              .build();
    });
    return stubs;
  }

  private static WorkflowServiceStubsOptions options() {
    return WorkflowServiceStubsOptions.newBuilder()
            .setTarget("127.0.0.1:7233")
            .build();
  }
}
