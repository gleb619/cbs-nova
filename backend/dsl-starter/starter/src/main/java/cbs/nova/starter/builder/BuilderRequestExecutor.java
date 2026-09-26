package cbs.nova.starter.builder;

import cbs.nova.starter.exception.BuilderClientBusyException;
import cbs.nova.starter.exception.BuilderUnavailableException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.client.ResourceAccessException;

@RequiredArgsConstructor
public class BuilderRequestExecutor {

  private final ThreadPoolBulkhead queue;
  private final Bulkhead bulkhead;
  private final CircuitBreaker breaker;

  public <T> T execute(Callable<T> call) {
    try {
      return submit(call).toCompletableFuture().get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while waiting for builder response", e);
    } catch (ExecutionException e) {
      throw propagate(e.getCause());
    }
  }

  <T> CompletionStage<T> submit(Callable<T> call) {
    try {
      return queue.submit(() -> runWithBulkhead(() -> runWithBreaker(() -> invoke(call))));
    } catch (BulkheadFullException e) {
      throw new BuilderClientBusyException("builder request queue is full");
    } catch (RejectedExecutionException e) {
      throw new BuilderClientBusyException("builder request queue is full");
    }
  }

  private <T> T runWithBulkhead(Callable<T> call) throws Exception {
    try {
      return bulkhead.executeCallable(call);
    } catch (BulkheadFullException e) {
      throw new IllegalStateException("builder client bulkhead saturated");
    }
  }

  private <T> T runWithBreaker(Callable<T> call) throws Exception {
    try {
      return breaker.executeCallable(call);
    } catch (CallNotPermittedException e) {
      throw new BuilderUnavailableException("DSL builder circuit breaker is open");
    }
  }

  private <T> T invoke(Callable<T> call) throws Exception {
    try {
      return call.call();
    } catch (ResourceAccessException e) {
      throw new BuilderUnavailableException("DSL builder unreachable: " + e.getMessage(), e);
    }
  }

  private RuntimeException propagate(Throwable throwable) {
    if (throwable instanceof RuntimeException runtimeException) {
      return runtimeException;
    }
    if (throwable instanceof Error error) {
      throw error;
    }
    return new IllegalStateException("builder call failed: " + throwable.getMessage(), throwable);
  }
}
