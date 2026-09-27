package cbs.nova.starter.service;

import cbs.nova.dsl.GlobalManager;
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
    SENT, NOT_FOUND, NOT_RUNNING, UNKNOWN_SIGNAL
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

    // T672: Temporal's WorkflowStub.signal() never throws for an undeclared signal name —
    // both the test server and a real server (1.25.x) silently drop it. Detect the
    // undeclared-signal condition from the process definition instead.
    if (isNotDeclaredSignal(run.processName(), signalName)) {
      log.warn("Signal {} is not declared by process {} (run {})", signalName,
              run.processName(), runId);
      return new SignalResult(Outcome.UNKNOWN_SIGNAL, run, run.status());
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

  private static boolean isNotDeclaredSignal(@Nullable String processName,
          @NonNull String signalName) {
    if (processName == null) {
      return false;
    }
    return GlobalManager.globalManager()
            .findProcess(processName)
            .map(process -> process.signals().stream()
                    .noneMatch(signal -> signal.name().equals(signalName)))
            .orElse(false);
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
