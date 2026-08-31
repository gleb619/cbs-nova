package cbs.nova.starter.service;

import lombok.Getter;

@Getter
public final class IdempotentReplayException extends RuntimeException {

  private final String runId;

  public IdempotentReplayException(String runId) {
    super("Workflow already started for run id " + runId);
    this.runId = runId;
  }

  public String runId() {
    return runId;
  }
}
