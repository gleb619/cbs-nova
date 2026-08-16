package cbs.nova.dsl;

import cbs.nova.dsl.DslObject.DslType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record GeneratedClassDescriptor(
        @NonNull String name,
        @NonNull DslType type,
        @NonNull String version,
        @NonNull String taskQueue,
        @NonNull Class<?> temporalInterface,
        @NonNull Class<?> temporalImplementation,
        @Nullable Class<?> inputType,
        @Nullable Class<?> outputType,
        @NonNull String executeJson) {
}
