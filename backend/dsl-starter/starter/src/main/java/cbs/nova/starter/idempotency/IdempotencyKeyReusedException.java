package cbs.nova.starter.idempotency;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class IdempotencyKeyReusedException extends RuntimeException {

  private final String runId;

  public String runId() {
    return runId;
  }

  @Override
  public String getMessage() {
    return "Idempotency key reused with different payload for run id " + runId;
  }
}
