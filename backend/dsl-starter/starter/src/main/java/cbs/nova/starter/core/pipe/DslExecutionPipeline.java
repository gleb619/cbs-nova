package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public final class DslExecutionPipeline<R> implements DslExecutionPipe<R> {

  private final List<DslPipeStage> stages;

  public static <R> Builder<R> builder() {
    return new Builder<>();
  }

  @Override
  public @NonNull Result<R> execute(@NonNull String name, @NonNull Context<?> ctx) {
    DslPipeContext context = new DslPipeContext(
            name, ctx, ctx.mode(), generateRunId(ctx));
    return executeStage(context, 0);
  }

  @SuppressWarnings("unchecked")
  private @NonNull Result<R> executeStage(@NonNull DslPipeContext context, int index) {
    if (index >= stages.size()) {
      return terminalResult(context);
    }
    DslPipeStage stage = stages.get(index);
    return (Result<R>) stage.execute(context, next -> executeStage(next, index + 1));
  }

  @SuppressWarnings("unchecked")
  private @NonNull Result<R> terminalResult(@NonNull DslPipeContext context) {
    Result<?> result = (Result<?>) context.getAttribute("dslResult");
    if (result == null) {
      throw new IllegalStateException(
              "No dispatch stage set dslResult for runId=" + context.getRunId());
    }
    return (Result<R>) result;
  }

  private @NonNull String generateRunId(@NonNull Context<?> ctx) {
    String runId = ctx.runId();
    return !runId.isBlank() ? runId : UUID.randomUUID().toString();
  }

  public static final class Builder<R> {

    private final List<DslPipeStage> stages = new ArrayList<>();

    public Builder<R> stage(@NonNull DslPipeStage stage) {
      stages.add(stage);
      return this;
    }

    public DslExecutionPipeline<R> build() {
      return new DslExecutionPipeline<>(stages);
    }
  }
}
