package cbs.nova.dsl.explain;

import static cbs.nova.dsl.config.Constants.EMPTY_MARKDOWN;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.ExplainSupport;
import cbs.nova.dsl.model.ExplainReport;
import org.jspecify.annotations.NonNull;

/**
 * Default descriptor-based explain report for executables that do not provide their own
 * {@link ExplainSupport} implementation.
 */
public final class ExecutableExplainDecorator {

  private ExecutableExplainDecorator() {
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static @NonNull ExplainReport explain(
          @NonNull Executable<?, ?> executable, @NonNull Context<?> ctx) {
    if (executable instanceof ExplainSupport explainable) {
      return (ExplainReport) explainable.explain(ctx);
    }
    var descriptor = executable.describe();
    var markdown = executable.description();
    var hasDescription = !EMPTY_MARKDOWN.equals(markdown) && !markdown.isBlank();
    var fallbackName = executable.getClass().getSimpleName();
    var name = descriptor.name() != null
            ? descriptor.name()
            : (fallbackName.isEmpty() ? "executable" : fallbackName);
    return ExplainReport.builder()
            .name(name)
            .description(hasDescription ? firstLine(markdown) : derivedDescription(descriptor))
            .markdown(hasDescription ? markdown : EMPTY_MARKDOWN)
            .build();
  }

  private static @NonNull String firstLine(@NonNull String markdown) {
    return markdown.lines()
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .findFirst()
            .orElse("");
  }

  private static @NonNull String derivedDescription(@NonNull ExecutableDescriptor descriptor) {
    var input = descriptor.inputType() != null ? descriptor.inputType().getSimpleName() : "untyped";
    var output = descriptor.outputType() != null
            ? descriptor.outputType().getSimpleName()
            : "untyped";
    return "Helper `" + descriptor.name() + "`: input `" + input + "`, output `" + output + "`";
  }
}
