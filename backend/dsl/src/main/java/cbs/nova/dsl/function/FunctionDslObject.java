package cbs.nova.dsl.function;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.FunctionContext;
import cbs.nova.dsl.ParameterDescriptor;
import cbs.nova.dsl.Result;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record FunctionDslObject(
        @NonNull String name,
        @Nullable List<ParameterDescriptor> parameters,
        @NonNull Function<FunctionContext<?>, Result<?>> executeLogic,
        @Nullable Function<FunctionContext<?>, Result<?>> previewLogic,
        @Nullable Supplier<DslDescriptor> descriptor) implements DslObject {
  @Override
  public @NonNull DslType type() {
    return DslType.FUNCTION;
  }

  public @NonNull Function<FunctionContext<?>, Result<?>> effectivePreview() {
    return previewLogic != null ? previewLogic : executeLogic;
  }

  public @NonNull DslDescriptor describe() {
    if (descriptor != null)
      return descriptor.get();
    return new DslDescriptor(
            name,
            DslType.FUNCTION,
            null,
            null,
            null,
            false,
            false,
            "delegates to execute",
            parameters != null ? parameters : List.of(),
            null,
            null,
            null,
            null);
  }
}
