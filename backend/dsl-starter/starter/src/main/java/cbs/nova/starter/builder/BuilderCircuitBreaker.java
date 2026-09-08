package cbs.nova.starter.builder;

import cbs.nova.starter.exception.BuilderUnavailableException;
import java.util.concurrent.Callable;
import java.util.function.LongSupplier;
import lombok.RequiredArgsConstructor;

//TODO: replace with a Resilience4j
@RequiredArgsConstructor
public class BuilderCircuitBreaker {

  enum State {
    CLOSED, OPEN, HALF_OPEN
  }

  private final int failureThreshold;
  private final long openDurationMillis;
  private final int halfOpenProbes;
  private final LongSupplier clock;

  private State state = State.CLOSED;
  private int failureCount;
  private long openedAtMillis;
  private int probeSuccesses;
  private int inFlightProbes;

  public <T> T execute(Callable<T> call) throws Exception {
    boolean halfOpenProbe = acquirePermit();
    try {
      T result = call.call();
      onSuccess();
      return result;
    } catch (BuilderUnavailableException e) {
      onFailure();
      throw e;
    } finally {
      releaseProbe(halfOpenProbe);
    }
  }

  public State state() {
    return state;
  }

  private synchronized boolean acquirePermit() {
    long now = clock.getAsLong();
    if (state == State.OPEN) {
      if (now - openedAtMillis < openDurationMillis) {
        throw new BuilderUnavailableException("DSL builder circuit breaker is open");
      }
      state = State.HALF_OPEN;
      probeSuccesses = 0;
      inFlightProbes = 0;
    }
    if (state == State.HALF_OPEN && inFlightProbes >= halfOpenProbes) {
      throw new BuilderUnavailableException(
              "DSL builder circuit breaker half-open probes exhausted");
    }
    if (state == State.HALF_OPEN) {
      inFlightProbes++;
      return true;
    }
    return false;
  }

  private synchronized void onSuccess() {
    if (state == State.HALF_OPEN) {
      probeSuccesses++;
      if (probeSuccesses >= halfOpenProbes) {
        state = State.CLOSED;
        failureCount = 0;
        probeSuccesses = 0;
        inFlightProbes = 0;
      }
      return;
    }
    failureCount = 0;
  }

  private synchronized void onFailure() {
    if (state == State.HALF_OPEN) {
      state = State.OPEN;
      openedAtMillis = clock.getAsLong();
      probeSuccesses = 0;
      inFlightProbes = 0;
      return;
    }
    failureCount++;
    if (failureCount >= failureThreshold) {
      state = State.OPEN;
      openedAtMillis = clock.getAsLong();
      failureCount = 0;
    }
  }

  private synchronized void releaseProbe(boolean halfOpenProbe) {
    if (halfOpenProbe && state == State.HALF_OPEN && inFlightProbes > 0) {
      inFlightProbes--;
    }
  }

}
