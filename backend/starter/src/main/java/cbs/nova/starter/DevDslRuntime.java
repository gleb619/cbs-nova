package cbs.nova.starter;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslRuntime;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.generator.BpmnDiagramGenerator;
import cbs.nova.dsl.generator.MermaidDiagramGenerator;
import cbs.nova.dsl.generator.PlantUmlDiagramGenerator;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DevDslRuntime implements DslRuntime {

  private final ExternalCallTracker externalCallTracker;
  private final ExecutionTraceCollector traceCollector;
  private final ContextFactory contextFactory;

  @Override
  public @NonNull Result<PreviewReport> preview(@NonNull String name, @NonNull Context<?> ctx) {
    List<ExternalCallTracker.CallDetail> calls = new ArrayList<>();
    String runId = runIdFor(ctx);
    externalCallTracker.startTracking(calls);
    traceCollector.start(runId);
    try {
      Result<?> result = dispatch(name,
              contextFactory.of(ctx.body(), ctx.metadata(), ExecutionMode.PREVIEW, runId),
              ExecutionMode.PREVIEW);
      List<String> trace = new ArrayList<>();
      trace.add("started: " + name);
      trace.add("mode: PREVIEW");
      trace.addAll(traceCollector.snapshot(runId));
      if (result.isSuccess()) {
        trace.add("completed successfully");
        PreviewReport report = new PreviewReport(
                name,
                ExecutionMode.PREVIEW,
                true,
                result.value(),
                List.copyOf(trace),
                toCallJson(calls),
                toCallCounts(calls));
        return Result.success(report);
      } else {
        trace.add("failed: " + result.cause().getMessage());
        return Result.failure(result.cause());
      }
    } finally {
      traceCollector.stop(runId);
      externalCallTracker.stopTracking();
    }
  }

  @Override
  public @NonNull Result<?> run(@NonNull String name, @NonNull Context<?> ctx) {
    List<ExternalCallTracker.CallDetail> calls = new ArrayList<>();
    externalCallTracker.startTracking(calls);
    try {
      return dispatch(name, ctx, ExecutionMode.RUN);
    } finally {
      externalCallTracker.stopTracking();
    }
  }

  @Override
  public @NonNull ExplainReport explain(@NonNull String name, @NonNull Context<?> ctx) {
    List<ExternalCallTracker.CallDetail> calls = new ArrayList<>();
    String runId = runIdFor(ctx);
    externalCallTracker.startTracking(calls);
    traceCollector.start(runId);
    try {
      Result<?> result = dispatch(name,
              contextFactory.of(ctx.body(), ctx.metadata(), ExecutionMode.EXPLAIN, runId),
              ExecutionMode.EXPLAIN);

      GlobalManager gm2 = GlobalManager.globalManager();
      String entityKind = gm2.hasProcess(name)
              ? "Process"
              : gm2.hasTransaction(name)
                      ? "Transaction"
                      : gm2.hasHelper(name) ? "Helper" : "Entity";
      String description = entityKind + ": " + name;

      var mermaidGen = new MermaidDiagramGenerator();
      var plantGen = new PlantUmlDiagramGenerator();
      var bpmnGen = new BpmnDiagramGenerator();

      String mermaid = gm2.findProcess(name)
              .map(mermaidGen::forProcess)
              .or(() -> gm2.findTransaction(name).map(mermaidGen::forTransaction))
              .orElseGet(() -> mermaidGen.forHelper(name));

      String plantUml = gm2.findProcess(name)
              .map(plantGen::forProcess)
              .or(() -> gm2.findTransaction(name).map(plantGen::forTransaction))
              .orElseGet(() -> plantGen.forHelper(name));

      String bpmn = gm2.findProcess(name)
              .map(bpmnGen::forProcess)
              .or(() -> gm2.findTransaction(name).map(bpmnGen::forTransaction))
              .orElseGet(() -> bpmnGen.forHelper(name));

      var trace = new ArrayList<String>();
      trace.add("started: " + name);
      trace.add("mode: EXPLAIN");
      trace.addAll(traceCollector.snapshot(runId));
      if (result.isSuccess()) {
        Object val = result.value();
        trace.add("result: " + (val != null ? val.toString() : "null"));
      } else {
        trace.add("result: failure: " + result.cause().getMessage());
      }

      DslDescriptor dslDesc = gm2.describeProcess(name)
              .or(() -> gm2.describeTransaction(name))
              .or(() -> gm2.describeFunction(name))
              .orElse(null);

      return new ExplainReport(
              name,
              description,
              mermaid,
              plantUml,
              bpmn,
              List.copyOf(trace),
              toCallJson(calls),
              toCallCounts(calls),
              gm2.describeHelper(name).orElse(null),
              dslDesc);
    } finally {
      traceCollector.stop(runId);
      externalCallTracker.stopTracking();
    }
  }

  private List<Map<String, Object>> toCallJson(List<ExternalCallTracker.CallDetail> calls) {
    List<Map<String, Object>> callsJson = new ArrayList<>();
    for (ExternalCallTracker.CallDetail call : calls) {
      Map<String, Object> callMap = new HashMap<>();
      callMap.put("type", call.type());
      callMap.put("target", call.target());
      callMap.put("operation", call.operation());
      callMap.put("timestamp", call.timestamp());
      callMap.put("metadata", call.metadata());
      callsJson.add(callMap);
    }
    return List.copyOf(callsJson);
  }

  private Map<String, Integer> toCallCounts(List<ExternalCallTracker.CallDetail> calls) {
    Map<String, Integer> counts = new HashMap<>();
    for (ExternalCallTracker.CallDetail call : calls) {
      counts.merge(call.type(), 1, Integer::sum);
    }
    return Map.copyOf(counts);
  }

  private Result<?> dispatch(String name, Context<?> ctx, ExecutionMode mode) {
    String runId = runIdFor(ctx);
    var modeCtx = contextFactory.of(ctx.body(), ctx.metadata(), mode, runId);
    GlobalManager gm = GlobalManager.globalManager();
    if (gm.hasProcess(name)) {
      return gm.runProcess(name, modeCtx);
    }
    if (gm.hasTransaction(name)) {
      return gm.runTransaction(name, modeCtx);
    }
    if (gm.hasHelper(name)) {
      return gm.runHelper(name, modeCtx);
    }
    return Result.failure(new IllegalArgumentException("No DSL entity registered: " + name));
  }

  private @NonNull String runIdFor(@NonNull Context<?> ctx) {
    String runId = ctx.runId();
    return (runId == null || runId.isBlank()) ? contextFactory.generateRunId() : runId;
  }
}
