package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.List;

public interface Executable<IN, OUT> {

  @NonNull
  default Result<OUT> preview(@NonNull Context<IN> ctx) {
    return execute(ctx);
  }

  @NonNull
  Result<OUT> execute(@NonNull Context<IN> ctx);

  @NonNull
  default ExecutableDescriptor describe() {
    //TODO: instead add to `ExecutableDescriptor` static method `ExecutableDescriptor.empty()`
    return new ExecutableDescriptor(null, null, null, null, true, "delegates to execute",
            List.of());
  }

  /**
   * Returns a brief description of the object formatted as Markdown text.
   * <p>
   * By default, this returns an empty Markdown HTML comment ({@code "<!-- NONE -->"})
   * which renders as invisible "nothing" in Markdown viewers.
   * </p>
   *
   * @return a Markdown-formatted string describing the object
   */
  default String description() {
    return "<!-- NONE -->";
  }

}
