package cbs.nova.dsl;

import static cbs.nova.dsl.config.Constants.EMPTY_MARKDOWN;

import cbs.nova.dsl.explain.ExecutableExplainDecorator;
import cbs.nova.dsl.model.ExplainReport;
import org.jspecify.annotations.NonNull;

@FunctionalInterface
public interface Executable<IN, OUT>
        extends
          PreviewSupport<IN, OUT>,
          DescribeSupport,
          DescriptionSupport {

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

  /**
   * Returns a default explain report derived from the executable descriptor and description.
   * Executables that provide their own explanation should implement {@link ExplainSupport}.
   */
  default @NonNull ExplainReport explain(@NonNull Context<IN> ctx) {
    return ExecutableExplainDecorator.explain(this, ctx);
  }
}
