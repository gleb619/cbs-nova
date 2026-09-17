package cbs.nova.dsl.explain;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.ExplainReport;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExplainResourceExplainer {

  public static @NonNull Function<Context<?>, Result<ExplainReport>> viaResource(
          @NonNull String name, @NonNull String resourcePath) {
    return ctx -> {
      final String markdown;
      try {
        markdown = ctx.bean(ExplainResourceResolver.class).load(resourcePath);
      } catch (RuntimeException ex) {
        return Result.failure(new IllegalStateException(
                "Unable to load explain resource for '" + name + "' from classpath: "
                        + ClasspathExplainResourceResolver.DEFAULT_PREFIX + resourcePath,
                ex));
      }
      return Result.success(ExplainReport.of(name, markdown));
    };
  }
}
