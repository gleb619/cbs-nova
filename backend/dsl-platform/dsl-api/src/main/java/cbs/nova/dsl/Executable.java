package cbs.nova.dsl;

import static cbs.nova.dsl.config.Constants.EMPTY_MARKDOWN;

import cbs.nova.dsl.model.ExplainReport;
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
  @Deprecated
  // TODO: remove impl, instead add a some decorator in PipeStage
  default @NonNull ExplainReport explain(@NonNull Context<IN> ctx) {
    var descriptor = describe();
    var markdown = description();
    var hasDescription = !EMPTY_MARKDOWN.equals(markdown) && !markdown.isBlank();
    var fallbackName = getClass().getSimpleName();
    var name = descriptor.name() != null
            ? descriptor.name()
            : (fallbackName.isEmpty() ? "executable" : fallbackName);
    return ExplainReport.builder()
            .name(name)
            .description(hasDescription ? firstLine(markdown) : derivedDescription(descriptor))
            .markdown(hasDescription ? markdown : EMPTY_MARKDOWN)
            .build();
  }

  @Deprecated(forRemoval = true)
  private static @NonNull String firstLine(@NonNull String markdown) {
    return markdown.lines()
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .findFirst()
            .orElse("");
  }

  @Deprecated(forRemoval = true)
  private static @NonNull String derivedDescription(@NonNull ExecutableDescriptor descriptor) {
    var input = descriptor.inputType() != null ? descriptor.inputType().getSimpleName() : "untyped";
    var output = descriptor.outputType() != null
            ? descriptor.outputType().getSimpleName()
            : "untyped";
    return "Helper `" + descriptor.name() + "`: input `" + input + "`, output `" + output + "`";
  }
}
