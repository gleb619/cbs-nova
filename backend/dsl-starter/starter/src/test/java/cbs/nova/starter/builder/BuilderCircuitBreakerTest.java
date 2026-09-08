package cbs.nova.starter.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.starter.exception.BuilderUnavailableException;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class BuilderCircuitBreakerTest {

  private static final long OPEN_DURATION_MILLIS = 10_000;

  private final AtomicLong now = new AtomicLong(1_000);
  private final BuilderCircuitBreaker breaker = new BuilderCircuitBreaker(2, OPEN_DURATION_MILLIS,
          2, now::get);

  @Test
  void closedBreakerAllowsCalls() throws Exception {
    assertThat(breaker.execute(() -> "ok")).isEqualTo("ok");
    assertThat(breaker.state()).isEqualTo(BuilderCircuitBreaker.State.CLOSED);
  }

  @Test
  void opensAfterFailureThreshold() {
    failTwice();

    assertThat(breaker.state()).isEqualTo(BuilderCircuitBreaker.State.OPEN);
    assertThatThrownBy(() -> breaker.execute(() -> "unreachable"))
            .isInstanceOf(BuilderUnavailableException.class);
  }

  @Test
  void successResetsClosedFailureCount() throws Exception {
    assertThatThrownBy(() -> breaker.execute(unavailable())).isInstanceOf(
            BuilderUnavailableException.class);
    breaker.execute(() -> "ok");

    assertThatThrownBy(() -> breaker.execute(unavailable())).isInstanceOf(
            BuilderUnavailableException.class);
    assertThat(breaker.state()).isEqualTo(BuilderCircuitBreaker.State.CLOSED);
  }

  @Test
  void openBreakerRejectsUntilDurationElapses() throws Exception {
    failTwice();

    now.addAndGet(OPEN_DURATION_MILLIS - 1);
    assertThatThrownBy(() -> breaker.execute(() -> "too early"))
            .isInstanceOf(BuilderUnavailableException.class);

    now.addAndGet(1);
    assertThat(breaker.state()).isEqualTo(BuilderCircuitBreaker.State.OPEN);
    assertThat(breaker.execute(() -> "probe")).isEqualTo("probe");
    assertThat(breaker.state()).isEqualTo(BuilderCircuitBreaker.State.HALF_OPEN);
  }

  @Test
  void halfOpenClosesAfterEnoughProbeSuccesses() throws Exception {
    failTwice();
    now.addAndGet(OPEN_DURATION_MILLIS);

    breaker.execute(() -> "probe-1");
    assertThat(breaker.state()).isEqualTo(BuilderCircuitBreaker.State.HALF_OPEN);
    breaker.execute(() -> "probe-2");

    assertThat(breaker.state()).isEqualTo(BuilderCircuitBreaker.State.CLOSED);
  }

  @Test
  void halfOpenProbeFailureReopens() {
    failTwice();
    now.addAndGet(OPEN_DURATION_MILLIS);

    assertThatThrownBy(() -> breaker.execute(unavailable())).isInstanceOf(
            BuilderUnavailableException.class);

    assertThat(breaker.state()).isEqualTo(BuilderCircuitBreaker.State.OPEN);
    assertThatThrownBy(() -> breaker.execute(() -> "still open"))
            .isInstanceOf(BuilderUnavailableException.class);
  }

  @Test
  void reopenedBreakerUsesFreshClockWindow() throws Exception {
    failTwice();
    now.addAndGet(OPEN_DURATION_MILLIS);
    assertThatThrownBy(() -> breaker.execute(unavailable())).isInstanceOf(
            BuilderUnavailableException.class);

    now.addAndGet(OPEN_DURATION_MILLIS);
    assertThat(breaker.execute(() -> "probe")).isEqualTo("probe");
  }

  @Test
  void nonInfrastructureExceptionsDoNotCountAsFailures() {
    assertThatThrownBy(() -> breaker.execute(() -> {
      throw new IllegalArgumentException("bad request");
    })).isInstanceOf(IllegalArgumentException.class);

    assertThat(breaker.state()).isEqualTo(BuilderCircuitBreaker.State.CLOSED);
  }

  private void failTwice() {
    assertThatThrownBy(() -> breaker.execute(unavailable())).isInstanceOf(
            BuilderUnavailableException.class);
    assertThatThrownBy(() -> breaker.execute(unavailable())).isInstanceOf(
            BuilderUnavailableException.class);
  }

  private Callable<String> unavailable() {
    return () -> {
      throw new BuilderUnavailableException("builder down");
    };
  }

}
