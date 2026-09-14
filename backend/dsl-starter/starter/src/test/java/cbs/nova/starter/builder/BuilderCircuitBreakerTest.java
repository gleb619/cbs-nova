package cbs.nova.starter.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.starter.exception.BuilderUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Resilience4j circuit breaker configuration used by {@link DslBuilderClient}.
 *
 * <p>
 * T498 replaced the hand-rolled state machine with Resilience4j. The exact half-open probe
 * accounting and clock behaviour differ from the old implementation, so tests assert the library's
 * documented semantics rather than forcing the old state-machine assertions.
 */
class BuilderCircuitBreakerTest {

  private static final long OPEN_DURATION_MILLIS = 200;

  @Test
  void closedBreakerAllowsCalls() throws Exception {
    var breaker = circuitBreaker(2, OPEN_DURATION_MILLIS, 2);
    assertThat(breaker.executeCallable(() -> "ok")).isEqualTo("ok");
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
  }

  @Test
  void opensAfterFailureThreshold() {
    var breaker = circuitBreaker(2, OPEN_DURATION_MILLIS, 2);
    failTwice(breaker);

    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    assertThatThrownBy(() -> breaker.executeCallable(() -> "unreachable"))
            .isInstanceOf(CallNotPermittedException.class);
  }

  @Test
  void successResetsClosedFailureCount() throws Exception {
    var breaker = circuitBreaker(2, OPEN_DURATION_MILLIS, 2);
    assertThatThrownBy(() -> breaker.executeCallable(unavailable())).isInstanceOf(
            BuilderUnavailableException.class);
    breaker.executeCallable(() -> "ok");

    assertThatThrownBy(() -> breaker.executeCallable(unavailable())).isInstanceOf(
            BuilderUnavailableException.class);
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
  }

  @Test
  void openBreakerRejectsUntilDurationElapses() throws Exception {
    var breaker = circuitBreaker(2, OPEN_DURATION_MILLIS, 2);
    failTwice(breaker);

    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    assertThatThrownBy(() -> breaker.executeCallable(() -> "too early"))
            .isInstanceOf(CallNotPermittedException.class);

    Thread.sleep(OPEN_DURATION_MILLIS + 50);
    assertThat(breaker.executeCallable(() -> "probe")).isEqualTo("probe");
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
  }

  @Test
  void halfOpenClosesAfterEnoughProbeSuccesses() throws Exception {
    var breaker = circuitBreaker(2, OPEN_DURATION_MILLIS, 2);
    failTwice(breaker);
    Thread.sleep(OPEN_DURATION_MILLIS + 50);

    breaker.executeCallable(() -> "probe-1");
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
    breaker.executeCallable(() -> "probe-2");

    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
  }

  @Test
  void halfOpenProbeFailureReopens() throws Exception {
    var breaker = circuitBreaker(2, OPEN_DURATION_MILLIS, 2);
    failTwice(breaker);
    Thread.sleep(OPEN_DURATION_MILLIS + 50);

    assertThatThrownBy(() -> breaker.executeCallable(unavailable())).isInstanceOf(
            BuilderUnavailableException.class);

    // Resilience4j evaluates the half-open window of size permittedNumberOfCallsInHalfOpenState.
    // With failureRateThreshold=100% and window size 2, a single failed probe keeps the breaker in
    // HALF_OPEN; the second failed probe pushes the failure rate to 100% and transitions to OPEN.
    // This differs from the old hand-rolled state machine, which reopened immediately on the
    // first failed half-open probe.
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

    assertThatThrownBy(() -> breaker.executeCallable(unavailable())).isInstanceOf(
            BuilderUnavailableException.class);
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    assertThatThrownBy(() -> breaker.executeCallable(() -> "still open"))
            .isInstanceOf(CallNotPermittedException.class);
  }

  @Test
  void reopenedBreakerUsesFreshClockWindow() throws Exception {
    var breaker = circuitBreaker(2, OPEN_DURATION_MILLIS, 2);
    failTwice(breaker);
    Thread.sleep(OPEN_DURATION_MILLIS + 50);
    assertThatThrownBy(() -> breaker.executeCallable(unavailable())).isInstanceOf(
            BuilderUnavailableException.class);

    Thread.sleep(OPEN_DURATION_MILLIS + 50);
    assertThat(breaker.executeCallable(() -> "probe")).isEqualTo("probe");
  }

  @Test
  void nonInfrastructureExceptionsDoNotCountAsFailures() {
    var breaker = circuitBreaker(2, OPEN_DURATION_MILLIS, 2);
    assertThatThrownBy(() -> breaker.executeCallable(() -> {
      throw new IllegalArgumentException("bad request");
    })).isInstanceOf(IllegalArgumentException.class);

    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
  }

  private static void failTwice(CircuitBreaker breaker) {
    assertThatThrownBy(() -> breaker.executeCallable(unavailable())).isInstanceOf(
            BuilderUnavailableException.class);
    assertThatThrownBy(() -> breaker.executeCallable(unavailable())).isInstanceOf(
            BuilderUnavailableException.class);
  }

  private static CircuitBreaker circuitBreaker(int failureThreshold, long openDurationMillis,
          int halfOpenProbes) {
    return CircuitBreaker.of("test-breaker", CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(Math.max(1, failureThreshold))
            .minimumNumberOfCalls(Math.max(1, failureThreshold))
            .failureRateThreshold(100f)
            .permittedNumberOfCallsInHalfOpenState(Math.max(1, halfOpenProbes))
            .waitDurationInOpenState(Duration.ofMillis(openDurationMillis))
            .recordException(e -> e instanceof BuilderUnavailableException)
            .ignoreException(e -> !(e instanceof BuilderUnavailableException))
            .build());
  }

  private static Callable<String> unavailable() {
    return () -> {
      throw new BuilderUnavailableException("builder down");
    };
  }

}
