package cbs.nova.starter.service;

import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.DslRunStatus;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowStub;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DslSignalService {

  private final WorkflowClient workflowClient;
  private final DslRunRepository runRepository;
  private final ObjectProvider<OpenTelemetry> openTelemetryProvider;

  @Autowired
  public DslSignalService(WorkflowClient workflowClient, DslRunRepository runRepository,
          ObjectProvider<OpenTelemetry> openTelemetryProvider) {
    this.workflowClient = workflowClient;
    this.runRepository = runRepository;
    this.openTelemetryProvider = openTelemetryProvider;
  }

  /**
   * Legacy constructor used by unit tests that do not exercise the telemetry path. Resolves to a
   * no-op tracer so {@code DslSignalServiceTest} and the integration tests stay green without an
   * {@link OpenTelemetry} bean on the classpath.
   */
  public DslSignalService(WorkflowClient workflowClient, DslRunRepository runRepository) {
    this(workflowClient, runRepository, EmptyObjectProvider.of(OpenTelemetry.class));
  }

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
    OpenTelemetry openTelemetry = openTelemetryProvider.getIfAvailable();
    Tracer tracer = openTelemetry != null ? openTelemetry.getTracer("cbs.nova.dsl") : null;
    Span span = tracer != null
            ? tracer.spanBuilder("dsl.signal.send")
                    .setAttribute("runId", runId)
                    .setAttribute("signalName", signalName)
                    .startSpan()
            : null;
    try (Scope ignored = span != null ? span.makeCurrent() : null) {
      return doSendSignal(runId, signalName, payload, span);
    } catch (RuntimeException e) {
      if (span != null) {
        span.recordException(e);
        span.setStatus(StatusCode.ERROR, e.getMessage());
      }
      throw e;
    } finally {
      if (span != null) {
        span.end();
      }
    }
  }

  private @NonNull SignalResult doSendSignal(@NonNull String runId, @NonNull String signalName,
          @Nullable Object payload, @Nullable Span span) {
    Optional<DslRun> existing = runRepository.findByRunId(runId);
    if (existing.isEmpty()) {
      tagOutcome(span, "not_found");
      return new SignalResult(Outcome.NOT_FOUND, null, null);
    }

    DslRun run = existing.get();
    if (!DslRunStatus.RUNNING.name().equals(run.status())) {
      tagOutcome(span, "not_running");
      return new SignalResult(Outcome.NOT_RUNNING, run, run.status());
    }

    // T672: Temporal's WorkflowStub.signal() never throws for an undeclared signal name —
    // both the test server and a real server (1.25.x) silently drop it. Detect the
    // undeclared-signal condition from the process definition instead.
    if (isNotDeclaredSignal(run.processName(), signalName)) {
      log.warn("Signal {} is not declared by process {} (run {})", signalName,
              run.processName(), runId);
      tagOutcome(span, "unknown_signal");
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
      tagOutcome(span, "sent");
      return new SignalResult(Outcome.SENT, run, run.status());
    } catch (RuntimeException e) {
      tagOutcome(span, "error");
      log.warn("Failed to send signal {} to run {}: {}", signalName, runId, e.getMessage());
      throw e;
    }
  }

  private static void tagOutcome(@Nullable Span span, @NonNull String outcome) {
    if (span != null) {
      span.setAttribute("outcome", outcome);
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
    OpenTelemetry openTelemetry = openTelemetryProvider.getIfAvailable();
    Tracer tracer = openTelemetry != null ? openTelemetry.getTracer("cbs.nova.dsl") : null;
    Span span = tracer != null
            ? tracer.spanBuilder("dsl.signal.query")
                    .setAttribute("runId", runId)
                    .startSpan()
            : null;
    try (Scope ignored = span != null ? span.makeCurrent() : null) {
      return doQuerySignalState(runId, span);
    } catch (RuntimeException e) {
      if (span != null) {
        span.recordException(e);
        span.setStatus(StatusCode.ERROR, e.getMessage());
      }
      throw e;
    } finally {
      if (span != null) {
        span.end();
      }
    }
  }

  private @Nullable Map<String, Object> doQuerySignalState(@NonNull String runId,
          @Nullable Span span) {
    Optional<DslRun> existing = runRepository.findByRunId(runId);
    if (existing.isEmpty()) {
      tagOutcome(span, "not_found");
      return null;
    }

    DslRun run = existing.get();
    if (!DslRunStatus.RUNNING.name().equals(run.status())) {
      tagOutcome(span, "not_running");
      return Map.of();
    }

    try {
      WorkflowStub stub = workflowClient.newUntypedWorkflowStub(runId);
      Map<String, Object> state = stub.query("dslSignalState", Map.class);
      tagOutcome(span, "ok");
      return state;
    } catch (RuntimeException e) {
      tagOutcome(span, "error");
      log.warn("Failed to query signal state for run {}: {}", runId, e.getMessage());
      return Map.of();
    }
  }
}
