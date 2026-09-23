package cbs.nova.dsl.explain;

import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.model.ExplainReport;
import java.util.function.Function;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class DefaultExplainLogic<C> implements Function<C, Result<ExplainReport>> {

  private final String name;
  private final String fallbackDescription;

  private DefaultExplainLogic(@NonNull String name, @Nullable String fallbackDescription) {
    this.name = name;
    this.fallbackDescription = fallbackDescription;
  }

  public static <C> @NonNull DefaultExplainLogic<C> forObject(
          @NonNull String name, @Nullable String fallbackDescription) {
    return new DefaultExplainLogic<>(name, fallbackDescription);
  }

  @Override
  public @NonNull Result<ExplainReport> apply(C ctx) {
    return Result.success(
            ExplainReport.builder()
                    .name(name)
                    .description(GlobalManager.globalManager().description(name)
                            .orElse(fallbackDescription == null
                                    ? Constants.EMPTY_MARKDOWN
                                    : fallbackDescription))
                    .markdown(GlobalManager.globalManager().resolveExplainContent(name))
                    .build());
  }
}
