package cbs.nova.dsl;

import cbs.nova.dsl.DslObject.DslType;
import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

@Builder
public record DslDescriptor(
        @NonNull String name,
        @NonNull DslType type,
        @Nullable String description,
        @Nullable Class<?> inputType,
        @Nullable Class<?> outputType,
        boolean hasSideEffects,
        @NonNull List<ParameterDescriptor> parameters,
        @Nullable String taskQueue,
        @Nullable String version,
        @Nullable Duration startToCloseTimeout,
        @Nullable Duration heartbeatTimeout) {
}
