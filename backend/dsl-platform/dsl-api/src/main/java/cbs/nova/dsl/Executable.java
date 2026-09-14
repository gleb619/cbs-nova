package cbs.nova.dsl;

import static cbs.nova.dsl.config.Constants.EMPTY_MARKDOWN;

import cbs.nova.dsl.explain.ExplainBudget;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.model.ExplainReports;
import org.jspecify.annotations.NonNull;

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
    return EMPTY_MARKDOWN;
  }

  @Override
  default @NonNull ExplainReport explain(@NonNull Context<IN> ctx) {
    return defaultReport(this, ctx);
  }

  // TODO: it's forbidden to `truncateTo`, without traverse a whole graph
  @Deprecated(forRemoval = true)
  private static @NonNull ExplainReport defaultReport(
          @NonNull Executable<?, ?> executable, @NonNull Context<?> ctx) {
    var descriptor = executable.describe();
    var markdown = executable.description();
    var fallbackName = executable.getClass().getSimpleName();
    var name = descriptor.name() != null
            ? descriptor.name()
            : (fallbackName.isEmpty() ? "executable" : fallbackName);
    var report = new ExplainReport(
            name,
            EMPTY_MARKDOWN.equals(markdown) ? derivedDescription(descriptor) : markdown,
            "");
    return ExplainReports.truncateTo(report, ExplainBudget.of(ctx));
  }

  private static @NonNull String derivedDescription(@NonNull ExecutableDescriptor descriptor) {
    var input = descriptor.inputType() != null ? descriptor.inputType().getSimpleName() : "untyped";
    var output = descriptor.outputType() != null
            ? descriptor.outputType().getSimpleName()
            : "untyped";
    var sideEffects = descriptor.hasSideEffects() ? "has side effects" : "side-effect free";
    return "Helper `" + descriptor.name() + "`: input `" + input + "`, output `" + output + "`, "
            + sideEffects;
  }
}
