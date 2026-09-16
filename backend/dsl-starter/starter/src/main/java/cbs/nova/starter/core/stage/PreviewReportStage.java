package cbs.nova.starter.core.stage;

import cbs.nova.dsl.CallNode;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.dsl.PreviewMetricsSnapshot;
import cbs.nova.dsl.model.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.starter.core.PreviewErrorHandler;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.converter.ExternalCallConverter;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.core.recorder.ExternalCall;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public final class PreviewReportStage implements DslPipeStage {

  @Override
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    next.proceed(context);
    Result<?> dslResult = (Result<?>) context.getAttribute(StarterConstants.DSL_RESULT_ATTRIBUTE);

    boolean success = dslResult != null && dslResult.isSuccess();
    Object output = success ? dslResult.value() : null;
    List<ErrorResponse> errors = new ArrayList<>();
    if (dslResult != null && !dslResult.isSuccess()) {
      errors.add(PreviewErrorHandler.from(dslResult.cause(), context.name()));
    }

    @SuppressWarnings("unchecked")
    List<ExternalCall> calls = (List<ExternalCall>) context.getAttribute(
            StarterConstants.EXTERNAL_CALLS_ATTRIBUTE);
    List<Map<String, Object>> externalCalls = calls != null
            ? ExternalCallConverter.toCallJson(calls)
            : List.of();
    Map<String, Integer> callCounts = calls != null
            ? ExternalCallConverter.toCallCounts(calls)
            : Map.of();

    PreviewReport report = new PreviewReport(
            context.name(),
            ExecutionMode.PREVIEW,
            success,
            output,
            attribute(context, StarterConstants.EXECUTION_TRACE_ATTRIBUTE, List.class, List.of()),
            externalCalls,
            callCounts,
            context.getAttribute(StarterConstants.AST_TREE_ATTRIBUTE, CallNode.class),
            attribute(context, StarterConstants.DRY_RUN_LOGS_ATTRIBUTE, List.class, List.of()),
            context.getAttribute(StarterConstants.METRICS_ATTRIBUTE, PreviewMetricsSnapshot.class),
            errors);

    return Result.success(report);
  }

  @SuppressWarnings("unchecked")
  private <T> T attribute(@NonNull DslPipeContext context, @NonNull String key,
          @NonNull Class<T> type, T defaultValue) {
    T value = context.getAttribute(key, type);
    return value != null ? value : defaultValue;
  }
}
