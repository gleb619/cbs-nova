package cbs.nova.dsl.function;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.FunctionContext;
import cbs.nova.dsl.ParameterDescriptor;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.ExplainReport;
import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

@Builder
public record FunctionDslObject(
        @NonNull String name,
        @NonNull List<ParameterDescriptor> parameters,
        @Nullable Class<?> inputType,
        @Nullable Class<?> outputType,
        @NonNull Function<FunctionContext<?>, Result<?>> executeLogic,
        @NonNull Function<FunctionContext<?>, Result<?>> previewLogic,
        @NonNull Function<FunctionContext<?>, Result<ExplainReport>> explainLogic,
        @NonNull Supplier<DslDescriptor> descriptor,
        @Nullable String description) implements DslObject {

  @Override
  public @NonNull DslType type() {
    return DslType.FUNCTION;
  }

}
