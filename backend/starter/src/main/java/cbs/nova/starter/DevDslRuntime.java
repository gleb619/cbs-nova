package cbs.nova.starter;

import cbs.nova.dsl.BpmnDiagramGenerator;
import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslRuntime;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.MermaidDiagramGenerator;
import cbs.nova.dsl.PlantUmlDiagramGenerator;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.SimpleContext;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DevDslRuntime implements DslRuntime {

  @Autowired(required = false)
  private ExternalCallTracker externalCallTracker;

  @Override
  public @NonNull Result<PreviewReport> preview(@NonNull String name, @NonNull Context<?> ctx) {
    List<ExternalCallTracker.CallDetail> calls = new ArrayList<>();
    ExternalCallTracker.startTracking(calls);
    ExecutionTraceCollector.start();
    try {
      Result<?> result = dispatch(name, ctx, ExecutionMode.PREVIEW);
      List<String> trace = new ArrayList<>();
      trace.add("started: " + name);
      trace.add("mode: PREVIEW");
      trace.addAll(ExecutionTraceCollector.snapshot());
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
      ExecutionTraceCollector.stop();
      ExternalCallTracker.stopTracking();
    }
  }

  @Override
  public @NonNull Result<?> run(@NonNull String name, @NonNull Context<?> ctx) {
    List<ExternalCallTracker.CallDetail> calls = new ArrayList<>();
    ExternalCallTracker.startTracking(calls);
    try {
      return dispatch(name, ctx, ExecutionMode.RUN);
    } finally {
      ExternalCallTracker.stopTracking();
    }
  }

  @Override
  public @NonNull ExplainReport explain(@NonNull String name, @NonNull Context<?> ctx) {
    List<ExternalCallTracker.CallDetail> calls = new ArrayList<>();
    ExternalCallTracker.startTracking(calls);
    ExecutionTraceCollector.start();
    try {
      Result<?> result = dispatch(name, ctx, ExecutionMode.EXPLAIN);
      String description = result.isSuccess()
              ? "Executed " + name + " successfully"
              : "Execution of " + name + " failed: " + result.cause().getMessage();

      GlobalManager gm2 = GlobalManager.getInstance();
      String mermaid = gm2.findProcess(name)
              .map(MermaidDiagramGenerator::forProcess)
              .or(() -> gm2.findTransaction(name).map(MermaidDiagramGenerator::forTransaction))
              .orElse(MermaidDiagramGenerator.forHelper(name));

      String plantUml = gm2.findProcess(name)
              .map(PlantUmlDiagramGenerator::forProcess)
              .or(() -> gm2.findTransaction(name).map(PlantUmlDiagramGenerator::forTransaction))
              .orElse(PlantUmlDiagramGenerator.forHelper(name));

      String bpmn = gm2.findProcess(name)
              .map(BpmnDiagramGenerator::forProcess)
              .or(() -> gm2.findTransaction(name).map(BpmnDiagramGenerator::forTransaction))
              .orElse(BpmnDiagramGenerator.forHelper(name));

      var trace = new java.util.ArrayList<String>();
      trace.add("started: " + name);
      trace.add("mode: EXPLAIN");
      trace.addAll(ExecutionTraceCollector.snapshot());
      if (result.isSuccess()) {
        Object val = result.value();
        trace.add("result: " + (val != null ? val.toString() : "null"));
      } else {
        trace.add("result: failure: " + result.cause().getMessage());
      }

      return new ExplainReport(
              name,
              description,
              mermaid,
              plantUml,
              bpmn,
              List.copyOf(trace),
              toCallJson(calls),
              toCallCounts(calls));
    } finally {
      ExecutionTraceCollector.stop();
      ExternalCallTracker.stopTracking();
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
    String runId = (ctx.runId() == null || ctx.runId().isBlank())
            ? SimpleContext.generateRunId()
            : ctx.runId();
    var modeCtx = SimpleContext.of(ctx.body(), ctx.metadata(), mode, runId);
    GlobalManager gm = GlobalManager.getInstance();
    if (gm.hasProcess(name))
      return gm.runProcess(name, modeCtx);
    if (gm.hasTransaction(name))
      return gm.runTransaction(name, modeCtx);
    if (gm.hasHelper(name))
      return gm.runHelper(name, modeCtx);
    return Result.failure(new IllegalArgumentException("No DSL entity registered: " + name));
  }
}
