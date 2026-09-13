package cbs.nova.dsl.process;

import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Builder
public record ProcessDescriptor(
        @NonNull String name,
        @NonNull String version,
        @NonNull String taskQueue,
        @Nullable Class<?> inputType,
        @Nullable Class<?> outputType,
        boolean hasCompensation,
        @NonNull List<String> helperRefs,
        @NonNull List<String> transactionRefs) {

}
