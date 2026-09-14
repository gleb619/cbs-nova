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
        @Nullable Class<?> inputType,
        @Nullable Class<?> outputType) implements ObjectDescriptor {

  @Override
  public DslDescriptor toDslDescriptor() {
    return DslDescriptor.builder()
            .name(name)
            .type(DslType.FUNCTION)
            .inputType(inputType)
            .outputType(outputType)
            .hasSideEffects(false)
            .parameters(List.of())
            .objectDescriptor(this)
            .build();
  }

}
