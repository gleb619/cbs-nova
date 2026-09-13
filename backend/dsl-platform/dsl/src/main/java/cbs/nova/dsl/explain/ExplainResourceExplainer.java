package cbs.nova.dsl.explain;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.explain.ExplainBudget;
import java.util.function.Function;
import org.jspecify.annotations.NonNull;

public final class ExplainResourceExplainer {

  private ExplainResourceExplainer() {
  }

  public static @NonNull Function<Context<?>, Result<ExplainReport>> viaResource(
          @NonNull String name, @NonNull String resourcePath) {
    return ctx -> {
      final String markdown;
      try {
        markdown = DslConfig.dslConfig().explainResourceResolver().get().load(resourcePath);
      } catch (RuntimeException ex) {
        return Result.failure(new IllegalStateException(
                "Unable to load explain resource for '" + name + "' from classpath: "
                        + ClasspathExplainResourceResolver.DEFAULT_PREFIX + resourcePath,
                ex));
      }
      return Result.success(new ExplainReport(name, markdown, "")
              .truncateTo(ExplainBudget.of(ctx)));
    };
  }
}
