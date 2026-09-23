package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record ExecutableDescriptor(
        @Nullable String name,
        @Nullable String description,
        @Nullable Class<?> inputType,
        @Nullable Class<?> outputType,
        @NonNull List<ParameterDescriptor> parameters) {

  public static ExecutableDescriptor empty() {
    return new ExecutableDescriptor(null, null, null, null,
            List.of());
  }

}
