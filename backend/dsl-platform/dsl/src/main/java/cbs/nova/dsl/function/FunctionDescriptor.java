package cbs.nova.dsl.function;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record FunctionDescriptor(
        @NonNull String name,
        @Nullable Class<?> inputType,
        @Nullable Class<?> outputType) {

}
