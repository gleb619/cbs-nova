package cbs.nova.dsl;

import cbs.nova.dsl.model.ExplainReport;
import org.jspecify.annotations.NonNull;

import java.util.List;

@FunctionalInterface
public interface Executable<IN, OUT>
        extends
          PreviewSupport<IN, OUT>,
          DescribeSupport,
          DescriptionSupport,
          ExplainSupport<IN, ExplainReport> {

  @Override
  default @NonNull Result<OUT> preview(@NonNull Context<IN> ctx) {
    return execute(ctx);
  }

  @NonNull
  Result<OUT> execute(@NonNull Context<IN> ctx);

  @Override
  default @NonNull ExecutableDescriptor describe() {
    return ExecutableDescriptor.empty();
  }

  /**
   * Returns a brief description of the object formatted as Markdown text.
   * <p>
   * By default, this returns an empty Markdown HTML comment ({@code "<!-- NONE -->"}) which renders
   * as invisible "nothing" in Markdown viewers.
   * </p>
   *
   * @return a Markdown-formatted string describing the object
   */
  @NonNull
  @Override
  default String description() {
    return "<!-- NONE -->";
  }

  @Override
  default @NonNull ExplainReport explain(@NonNull Context<IN> ctx, int budgetChars) {
    var descriptor = describe();
    var markdown = description();
    var fallbackName = getClass().getSimpleName();
    var name = descriptor.name() != null
            ? descriptor.name()
            : (fallbackName.isEmpty() ? "executable" : fallbackName);
    var report = new ExplainReport(
            name,
            "<!-- NONE -->".equals(markdown) ? derivedDescription(descriptor) : markdown,
            "");
    return report.truncateTo(budgetChars);
  }

  private static @NonNull String derivedDescription(@NonNull ExecutableDescriptor descriptor) {
    var input = descriptor.inputType() != null ? descriptor.inputType().getSimpleName() : "untyped";
    var output = descriptor.outputType() != null
            ? descriptor.outputType().getSimpleName()
            : "untyped";
    var sideEffects = descriptor.hasSideEffects() ? "has side effects" : "is side-effect free";
    var parameters = descriptor.parameters().isEmpty()
            ? "declares no parameters"
            : "declares " + descriptor.parameters().size() + " parameter(s)";
    return "Executable that maps `" + input + "` to `" + output + "`, " + sideEffects + " and "
            + parameters + ".";
  }

}
