package cbs.nova.dsl;

import cbs.nova.dsl.DslObject.DslType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

public record DslDescriptor(
        @NonNull String name,
        @NonNull DslType type,
        @Nullable String description,
        @Nullable Class<?> inputType,
        @Nullable Class<?> outputType,
        boolean hasCompensation,
        boolean hasSideEffects,
        @Nullable String previewBehavior,
        @NonNull List<ParameterDescriptor> parameters,
        @Nullable String taskQueue,
        @Nullable String version,
        @Nullable Duration startToCloseTimeout,
        @Nullable Duration heartbeatTimeout) {

}
