package cbs.nova.dsl.process;

import cbs.nova.dsl.model.ObjectDescriptor;
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
        // TODO: remove next depricated lists
        @Deprecated(forRemoval = true) @NonNull List<String> helperRefs,
        @Deprecated(forRemoval = true) @NonNull List<String> transactionRefs)
        implements
          ObjectDescriptor {

}
