package cbs.nova.starter.exception;

import lombok.Getter;

/**
 * Thrown by {@code ExecutionSseService} / {@code DryRunLogSseService} when a {@code subscribe} call
 * would push the service past {@code cbs.sse.max-subscribers}. Mapped to {@code 429 Too Many
 * Requests} by {@code DslExceptionHandler} so the response shape matches every other DSL error.
 *
 * <p>
 * The emitter is intentionally never created — the {@code 429} is the first and only response the
 * client sees.
 */
@Getter
public class DslSubscriberCapExceededException extends RuntimeException {

  private final int cap;
  private final String id;

  public DslSubscriberCapExceededException(int cap, String id) {
    super("Max concurrent SSE subscribers (cap=%d) reached for id='%s'".formatted(cap, id));
    this.cap = cap;
    this.id = id;
  }
}
