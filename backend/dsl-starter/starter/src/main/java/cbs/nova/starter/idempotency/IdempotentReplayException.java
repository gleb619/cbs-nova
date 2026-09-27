package cbs.nova.starter.idempotency;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class IdempotentReplayException extends RuntimeException {

  private final String runId;

  public String runId() {
    return runId;
  }

  @Override
  public String getMessage() {
    return "Workflow already started for run id " + runId;
  }
}
