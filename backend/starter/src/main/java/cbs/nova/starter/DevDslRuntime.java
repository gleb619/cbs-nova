package cbs.nova.starter;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslRuntime;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.MermaidDiagramGenerator;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.SimpleContext;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DevDslRuntime implements DslRuntime {

  @Override
  public @NonNull Result<?> preview(@NonNull String name, @NonNull Context<?> ctx) {
    return dispatch(name, ctx, ExecutionMode.PREVIEW);
  }

  @Override
  public @NonNull Result<?> run(@NonNull String name, @NonNull Context<?> ctx) {
    return dispatch(name, ctx, ExecutionMode.RUN);
  }

  @Override
  public @NonNull ExplainReport explain(@NonNull String name, @NonNull Context<?> ctx) {
    Result<?> result = dispatch(name, ctx, ExecutionMode.EXPLAIN);
    String description = result.isSuccess()
            ? "Executed " + name + " successfully"
            : "Execution of " + name + " failed: " + result.cause().getMessage();
    GlobalManager gm2 = GlobalManager.getInstance();
    String mermaid = gm2.findProcess(name)
            .map(MermaidDiagramGenerator::forProcess)
            .or(() -> gm2.findTransaction(name).map(MermaidDiagramGenerator::forTransaction))
            .orElse(MermaidDiagramGenerator.forHelper(name));
    var trace = new java.util.ArrayList<String>();
    trace.add("started: " + name);
    trace.add("mode: EXPLAIN");
    if (result.isSuccess()) {
      Object val = result.value();
      trace.add("result: " + (val != null ? val.toString() : "null"));
    } else {
      trace.add("result: failure: " + result.cause().getMessage());
    }
    return new ExplainReport(name, description, mermaid, java.util.List.copyOf(trace));
  }

  private Result<?> dispatch(String name, Context<?> ctx, ExecutionMode mode) {
    var modeCtx = new SimpleContext<>(ctx.body(), ctx.metadata(), mode);
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
