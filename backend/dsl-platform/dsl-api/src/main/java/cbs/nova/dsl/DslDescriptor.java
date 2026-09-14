package cbs.nova.dsl;

import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.model.ObjectDescriptor;
import java.time.Duration;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

//TODO: change to a record
@Deprecated
@Builder
@Getter
@Accessors(fluent = true)
public final class DslDescriptor {

  @NonNull
  @Getter
  private final ObjectDescriptor objectDescriptor;
  @Builder.Default
  private final boolean hasSideEffects = false;
  @Builder.Default
  private final @NonNull List<ParameterDescriptor> parameters = List.of();
  @Nullable
  @Getter
  private final String taskQueue;
  @Nullable
  @Getter
  private final String version;
  @Nullable
  private final Duration startToCloseTimeout;
  @Nullable
  private final Duration heartbeatTimeout;

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
