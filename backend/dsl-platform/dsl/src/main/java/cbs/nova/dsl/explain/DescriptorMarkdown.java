package cbs.nova.dsl.explain;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.ParameterDescriptor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

//TODO: to remove, we need other format for a md
@Deprecated(forRemoval = true)
public final class DescriptorMarkdown {

  private DescriptorMarkdown() {
  }

  public static @NonNull String render(@NonNull DslDescriptor descriptor) {
    var sb = new StringBuilder();
    sb.append("**").append(capitalize(descriptor.type().name())).append("** `")
            .append(descriptor.name()).append('`');
    if (descriptor.description() != null && !descriptor.description().isBlank()) {
      sb.append("\n\n").append(descriptor.description());
    }
    sb.append("\n\n");
    sb.append("- Input: ").append(typeName(descriptor.inputType()));
    sb.append("\n- Output: ").append(typeName(descriptor.outputType()));
    sb.append("\n- Side effects: ").append(descriptor.hasSideEffects() ? "yes" : "no");
    if (!descriptor.parameters().isEmpty()) {
      sb.append("\n- Parameters: ");
      sb.append(String.join(", ",
              descriptor.parameters().stream().map(ParameterDescriptor::name).toList()));
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
