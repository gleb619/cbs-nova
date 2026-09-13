package cbs.nova.dsl.function;

import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Builder
public record FunctionDescriptor(
        @NonNull String name,
        @Nullable Class<?> inputType,
        @Nullable Class<?> outputType) {

}
