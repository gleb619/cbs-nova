package cbs.nova.starter.core.stage;

import cbs.nova.dsl.CallNode;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.PreviewErrorDetail;
import cbs.nova.dsl.PreviewMetricsSnapshot;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.starter.core.PreviewErrorHandler;
import cbs.nova.starter.core.StarterConstant;
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
    Result<?> dslResult = (Result<?>) context.getAttribute(StarterConstant.DSL_RESULT_ATTRIBUTE);

    boolean success = dslResult != null && dslResult.isSuccess();
    Object output = success ? dslResult.value() : null;
    List<PreviewErrorDetail> errors = new ArrayList<>();
    if (dslResult != null && !dslResult.isSuccess()) {
      errors.add(PreviewErrorHandler.from(dslResult.cause(), context.getName()));
    }

    @SuppressWarnings("unchecked")
    List<ExternalCall> calls = (List<ExternalCall>) context.getAttribute(
            StarterConstant.EXTERNAL_CALLS_ATTRIBUTE);
    List<Map<String, Object>> externalCalls = calls != null
            ? ExternalCallConverter.toCallJson(calls)
            : List.of();
    Map<String, Integer> callCounts = calls != null
            ? ExternalCallConverter.toCallCounts(calls)
            : Map.of();

    PreviewReport report = new PreviewReport(
            context.getName(),
            ExecutionMode.PREVIEW,
            success,
            output,
            attribute(context, StarterConstant.EXECUTION_TRACE_ATTRIBUTE, List.class, List.of()),
            externalCalls,
            callCounts,
            context.getAttribute(StarterConstant.AST_TREE_ATTRIBUTE, CallNode.class),
            attribute(context, StarterConstant.DRY_RUN_LOGS_ATTRIBUTE, List.class, List.of()),
            context.getAttribute(StarterConstant.METRICS_ATTRIBUTE, PreviewMetricsSnapshot.class),
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
