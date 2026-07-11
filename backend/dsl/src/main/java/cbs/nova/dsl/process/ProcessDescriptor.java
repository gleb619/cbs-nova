package cbs.nova.dsl.process;

import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record ProcessDescriptor(
        @NonNull String name,
        @NonNull String version,
        @NonNull String taskQueue,
        @Nullable Class<?> inputType,
        @Nullable Class<?> outputType,
        boolean hasCompensation,
        @NonNull List<String> helperRefs) {
}
