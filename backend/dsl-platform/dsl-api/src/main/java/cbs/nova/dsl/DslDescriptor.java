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
        boolean hasCompensation,
        boolean hasSideEffects,
        @NonNull List<ParameterDescriptor> parameters,
        @Nullable String taskQueue,
        @Nullable String version,
        @Nullable Duration startToCloseTimeout,
        @Nullable Duration heartbeatTimeout) {

  public @NonNull String explain() {
    var sb = new StringBuilder();
    sb.append("**").append(capitalize(type().name())).append("** `").append(name()).append('`');
    if (description() != null && !description().isBlank()) {
      sb.append("\n\n").append(description());
    }
    sb.append("\n\n");
    sb.append("- Input: ").append(typeName(inputType()));
    sb.append("\n- Output: ").append(typeName(outputType()));
    sb.append("\n- Side effects: ").append(hasSideEffects() ? "yes" : "no");
    sb.append("\n- Compensation: ").append(hasCompensation() ? "yes" : "no");
    if (!parameters().isEmpty()) {
      sb.append("\n- Parameters: ");
      sb.append(String.join(", ", parameters().stream().map(ParameterDescriptor::name).toList()));
    }
    return sb.toString();
  }

  private static @NonNull String typeName(@Nullable Class<?> type) {
    return type != null ? "`" + type.getSimpleName() + "`" : "untyped";
  }

  private static @NonNull String capitalize(@NonNull String value) {
    if (value.isEmpty()) {
      return value;
    }
    return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase();
  }
}
