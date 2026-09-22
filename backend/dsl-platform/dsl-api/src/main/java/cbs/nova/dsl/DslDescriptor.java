package cbs.nova.dsl;

import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.model.ObjectDescriptor;
import java.time.Duration;
import java.util.List;
import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Deprecated
@Builder
public record DslDescriptor(
        @NonNull ObjectDescriptor objectDescriptor,
        @Builder.Default boolean hasSideEffects,
        @Builder.Default @NonNull List<ParameterDescriptor> parameters,
        @Nullable String taskQueue,
        @Nullable String version,
        @Nullable Duration startToCloseTimeout,
        @Nullable Duration heartbeatTimeout) {

  public DslDescriptor {
    if (parameters == null) {
      parameters = List.of();
    }
  }

  public @NonNull String name() {
    return objectDescriptor.name();
  }

  public @NonNull DslType type() {
    return objectDescriptor.type();
  }

  public @Nullable String description() {
    return objectDescriptor.description();
  }

  public @Nullable Class<?> inputType() {
    return objectDescriptor.inputType();
  }

  public @Nullable Class<?> outputType() {
    return objectDescriptor.outputType();
  }
}
