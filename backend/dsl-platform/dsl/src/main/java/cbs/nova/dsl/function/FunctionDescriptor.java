package cbs.nova.dsl.function;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.model.ObjectDescriptor;
import java.util.List;
import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Builder
public record FunctionDescriptor(
        @NonNull String name,
        @Nullable String description,
        @Nullable Class<?> inputType,
        @Nullable Class<?> outputType) implements ObjectDescriptor {

  @Override
  public DslType type() {
    return DslType.FUNCTION;
  }

  @Override
  public DslDescriptor toDslDescriptor() {
    return DslDescriptor.builder()
            .objectDescriptor(this)
            .hasSideEffects(false)
            .parameters(List.of())
            .build();
  }

}
