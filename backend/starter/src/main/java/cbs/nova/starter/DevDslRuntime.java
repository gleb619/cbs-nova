package cbs.nova.starter;

import cbs.nova.dsl.CallNode;
import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslRuntime;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.ExecutionTreeCollector;
import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.PreviewMetricsSnapshot;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.TransactionRouting;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.generator.BpmnDiagramGenerator;
import cbs.nova.dsl.generator.MermaidDiagramGenerator;
import cbs.nova.dsl.generator.PlantUmlDiagramGenerator;
import cbs.nova.dsl.logging.DryRunLoggingContext;
import cbs.nova.starter.logging.DryRunLogEvent;
import cbs.nova.starter.logging.DryRunLogbackAppender;
import cbs.nova.starter.metrics.PreviewMetricsCollector;
import ch.qos.logback.classic.Logger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class DevDslRuntime implements DslRuntime {

  // TODO: remove ExternalCallTracker from here, instead add some system with
  // listerners/interceptors, for easility tracking/addingn new features
  private final ExternalCallTracker externalCallTracker;
  private final ExecutionTraceCollector traceCollector;
  private final ContextFactory contextFactory;
  private final DryRunLoggingContext dryRunLoggingContext;

  @Value("${cbs.nova.preview.callTree.maxDepth:32}")
  private final int previewCallTreeMaxDepth;

  @Override
  public @NonNull Result<PreviewReport> preview(@NonNull String name, @NonNull Context<?> ctx) {
    List<ExternalCallTracker.CallDetail> calls = new ArrayList<>();
    String runId = runIdFor(ctx);
    // TODO: we need to create a some new architecture, to reuse a tree collector across dsl calls,
    // we need a system with listerners/interceptors, for easility tracking/addingn new features
    var treeCollector = new ExecutionTreeCollector(previewCallTreeMaxDepth);
    externalCallTracker.startTracking(calls);
    traceCollector.start(runId);
    treeCollector.startRun(runId);
    Result<?>[] resultHolder = new Result[1];
    PreviewMetricsCollector metricsCollector = null;
    PreviewMetricsSnapshot metricsSnapshot = null;
    try {
      metricsCollector = PreviewMetricsCollector.start();
      dryRunLoggingContext.runWithRunId(runId, () -> {
        log.info("started: {}", name);
        log.info("mode: PREVIEW");
        resultHolder[0] = dispatch(name,
                contextFactory.of(ctx.body(), ctx.metadata(), ExecutionMode.PREVIEW, runId,
                        // TODO: why TransactionRouting is hardcoded here?
                        TransactionRouting.LOCAL, treeCollector));
        traceCollector.snapshot(runId).forEach(line -> log.info("{}", line));
        if (resultHolder[0].isSuccess()) {
          log.info("completed successfully");
        } else {
          log.info("failed: {}", resultHolder[0].cause().getMessage());
        }
      });
    } finally {
      if (metricsCollector != null) {
        var metricsTree = treeCollector.tree(runId).orElse(null);
        if (metricsTree != null) {
          countCallKinds(metricsTree, metricsCollector);
        }
        for (var call : calls) {
          metricsCollector.recordExternalCall(call.type());
        }
        metricsSnapshot = metricsCollector.stop();
      }
      // TODO: due refacto to async way of execution, we need another way of finish signal
      treeCollector.finishRun(runId);
      traceCollector.stop(runId);
      externalCallTracker.stopTracking();
    }

    Result<?> result = resultHolder[0];
    List<DryRunLogEvent> dryRunLogs = drainDryRunLogs(runId);
    List<String> trace = dryRunLogs.stream().map(DryRunLogEvent::message).toList();

    // TODO: instead add a new record that extends of some suprt type(e.g. Result implment it, and a
    // new Report type must implement it). As a result we just traverse troug a tree of Reports and
    // create a PreviewReport
    if (result.isSuccess()) {
      PreviewReport report = new PreviewReport(
              name,
              ExecutionMode.PREVIEW,
              true,
              result.value(),
              List.copyOf(trace),
              toCallJson(calls),
              toCallCounts(calls),
              treeCollector.tree(runId).orElse(null),
              toDryRunLogMaps(dryRunLogs),
              metricsSnapshot);
      return Result.success(report);
    } else {
      return Result.failure(result.cause());
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
  // TODO: refactor explain too, we need a super type, and a new implementation
  public @NonNull ExplainReport explain(@NonNull String name, @NonNull Context<?> ctx) {
    List<ExternalCallTracker.CallDetail> calls = new ArrayList<>();
    String runId = runIdFor(ctx);
    var treeCollector = new ExecutionTreeCollector(previewCallTreeMaxDepth);
    externalCallTracker.startTracking(calls);
    traceCollector.start(runId);
    treeCollector.startRun(runId);
    Result<?>[] resultHolder = new Result[1];
    PreviewMetricsCollector metricsCollector = null;
    PreviewMetricsSnapshot metricsSnapshot = null;
    try {
      metricsCollector = PreviewMetricsCollector.start();
      dryRunLoggingContext.runWithRunId(runId, () -> {
        resultHolder[0] = dispatch(name,
                contextFactory.of(ctx.body(), ctx.metadata(), ExecutionMode.EXPLAIN, runId,
                        TransactionRouting.LOCAL, treeCollector));
      });
    } finally {
      if (metricsCollector != null) {
        var metricsTree = treeCollector.tree(runId).orElse(null);
        if (metricsTree != null) {
          countCallKinds(metricsTree, metricsCollector);
        }
        for (var call : calls) {
          metricsCollector.recordExternalCall(call.type());
        }
        metricsSnapshot = metricsCollector.stop();
      }
      treeCollector.finishRun(runId);
      traceCollector.stop(runId);
      externalCallTracker.stopTracking();
    }

    GlobalManager gm2 = GlobalManager.globalManager();
    // TODO: it's a very bad solution, to add some strange if here, no, object itself, must know own
    // name/type or whatever
    String entityKind = gm2.hasProcess(name)
            ? "Process"
            : gm2.hasTransaction(name)
                    ? "Transaction"
                    : gm2.hasHelper(name) ? "Helper" : "Entity";
    String description = entityKind + ": " + name;

    var mermaidGen = new MermaidDiagramGenerator();
    var plantGen = new PlantUmlDiagramGenerator();
    var bpmnGen = new BpmnDiagramGenerator();

    // TODO: No, it's wrong, a new returned type(Explain) must jsut contain a some ast/tree that can
    // be converted to a mermaid
    String mermaid = gm2.findProcess(name)
            .map(mermaidGen::forProcess)
            .or(() -> gm2.findTransaction(name).map(mermaidGen::forTransaction))
            .orElseGet(() -> mermaidGen.forHelper(name));

    // TODO: No, it's wrong, a new returned type(Explain) must jsut contain a some ast/tree that can
    // be converted to a plantUml
    String plantUml = gm2.findProcess(name)
            .map(plantGen::forProcess)
            .or(() -> gm2.findTransaction(name).map(plantGen::forTransaction))
            .orElseGet(() -> plantGen.forHelper(name));

    // TODO: No, it's wrong, a new returned type(Explain) must jsut contain a some ast/tree that can
    // be converted to a bpmn
    String bpmn = gm2.findProcess(name)
            .map(bpmnGen::forProcess)
            .or(() -> gm2.findTransaction(name).map(bpmnGen::forTransaction))
            .orElseGet(() -> bpmnGen.forHelper(name));

    dryRunLoggingContext.runWithRunId(runId, () -> {
      log.info("started: {}", name);
      log.info("mode: EXPLAIN");
      traceCollector.snapshot(runId).forEach(line -> log.info("{}", line));
      if (resultHolder[0].isSuccess()) {
        Object val = resultHolder[0].value();
        log.info("result: {}", val != null ? val.toString() : "null");
      } else {
        log.info("result: failure: {}", resultHolder[0].cause().getMessage());
      }
    });

    List<DryRunLogEvent> dryRunLogs = drainDryRunLogs(runId);
    List<String> trace = dryRunLogs.stream().map(DryRunLogEvent::message).toList();

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
            dslDesc,
            treeCollector.tree(runId).orElse(null),
            toDryRunLogMaps(dryRunLogs),
            metricsSnapshot);
  }

  // TODO: no, it must be a separate class for convertation
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

  // TODO: no, it must be a separate class for convertation
  private Map<String, Integer> toCallCounts(List<ExternalCallTracker.CallDetail> calls) {
    Map<String, Integer> counts = new HashMap<>();
    for (ExternalCallTracker.CallDetail call : calls) {
      counts.merge(call.type(), 1, Integer::sum);
    }
    return Map.copyOf(counts);
  }

  private List<Map<String, Object>> toDryRunLogMaps(List<DryRunLogEvent> events) {
    List<Map<String, Object>> maps = new ArrayList<>();
    for (DryRunLogEvent event : events) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("level", event.level());
      map.put("message", event.message());
      map.put("timestamp", Instant.ofEpochMilli(event.timestampMillis()));
      map.put("mdc", event.mdc());
      map.put("runId", event.runId());
      maps.add(map);
    }
    return List.copyOf(maps);
  }

  // TODO: add optional field, for direct run, like enum with 3 values(process, transaction, helper)
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

  // TODO: remove junk method, it can be only one for dispatch
  @Deprecated(forRemoval = true)
  private Result<?> dispatch(String name, Context<?> ctx) {
    GlobalManager gm = GlobalManager.globalManager();
    if (gm.hasProcess(name)) {
      return gm.runProcess(name, ctx);
    }
    if (gm.hasTransaction(name)) {
      return gm.runTransaction(name, ctx);
    }
    if (gm.hasHelper(name)) {
      return gm.runHelper(name, ctx);
    }
    return Result.failure(new IllegalArgumentException("No DSL entity registered: " + name));
  }

  private void countCallKinds(CallNode node, PreviewMetricsCollector collector) {
    collector.recordCall(node.kind());
    for (CallNode child : node.children()) {
      countCallKinds(child, collector);
    }
  }

  private @NonNull String runIdFor(@NonNull Context<?> ctx) {
    String runId = ctx.runId();
    return (runId == null || runId.isBlank()) ? contextFactory.generateRunId() : runId;
  }

  private List<DryRunLogEvent> drainDryRunLogs(String runId) {
    var root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    var appender = root.getAppender("DRY_RUN");
    if (appender instanceof DryRunLogbackAppender dryRunAppender) {
      return dryRunAppender.drain(runId);
    }
    return List.of();
  }
}
