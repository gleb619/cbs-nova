package cbs.nova.dsl.process;

import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.model.ObjectDescriptor;
import java.util.List;
import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Builder
public record ProcessDescriptor(
        @NonNull String name,
        @Nullable String description,
        @NonNull String version,
        @NonNull String taskQueue,
        @Nullable Class<?> inputType,
        @Nullable Class<?> outputType,
        boolean hasCompensation,
        @Deprecated(forRemoval = true) @NonNull List<String> helperRefs,
        @Deprecated(forRemoval = true) @NonNull List<String> transactionRefs)
        implements
          ObjectDescriptor {

  @Override
  public DslType type() {
    return DslType.PROCESS;
  }

}
