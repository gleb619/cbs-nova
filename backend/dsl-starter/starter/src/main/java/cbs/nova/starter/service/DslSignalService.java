package cbs.nova.starter.service;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.DslRunStatus;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowStub;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DslSignalService {

  private final WorkflowClient workflowClient;
  private final DslRunRepository runRepository;

  public record SignalResult(
          @NonNull Outcome outcome,
          @Nullable DslRun run,
          @Nullable String currentStatus) {
  }

  public enum Outcome {
    SENT, NOT_FOUND, NOT_RUNNING
  }

  public @NonNull SignalResult sendSignal(
          @NonNull String runId,
          @NonNull String signalName,
          @Nullable Object payload) {
    Optional<DslRun> existing = runRepository.findByRunId(runId);
    if (existing.isEmpty()) {
      return new SignalResult(Outcome.NOT_FOUND, null, null);
    }

    DslRun run = existing.get();
    if (!DslRunStatus.RUNNING.name().equals(run.status())) {
      return new SignalResult(Outcome.NOT_RUNNING, run, run.status());
    }

    try {
      WorkflowStub stub = workflowClient.newUntypedWorkflowStub(runId);
      if (payload != null) {
        stub.signal(signalName, payload);
      } else {
        stub.signal(signalName);
      }
      log.info("Sent signal {} to run {} (process {})", signalName, runId, run.processName());
      return new SignalResult(Outcome.SENT, run, run.status());
    } catch (RuntimeException e) {
      log.warn("Failed to send signal {} to run {}: {}", signalName, runId, e.getMessage());
      throw e;
    }
  }

  public @Nullable Map<String, Object> querySignalState(@NonNull String runId) {
    Optional<DslRun> existing = runRepository.findByRunId(runId);
    if (existing.isEmpty()) {
      return null;
    }

    DslRun run = existing.get();
    if (!DslRunStatus.RUNNING.name().equals(run.status())) {
      return Map.of();
    }

    try {
      WorkflowStub stub = workflowClient.newUntypedWorkflowStub(runId);
      return stub.query("dslSignalState", Map.class);
    } catch (RuntimeException e) {
      log.warn("Failed to query signal state for run {}: {}", runId, e.getMessage());
      return Map.of();
    }
  }
}
