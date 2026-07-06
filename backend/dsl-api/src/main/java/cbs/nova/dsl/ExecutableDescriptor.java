package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record ExecutableDescriptor(
        @Nullable String name,
        @Nullable String description,
        @Nullable Class<?> inputType,
        @Nullable Class<?> outputType,
        boolean hasSideEffects,
        @Nullable String previewBehavior,
        @NonNull List<ParameterDescriptor> parameters) {
}
