package cbs.nova.starter.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

//TODO: move to a correspondent package
@Getter
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
