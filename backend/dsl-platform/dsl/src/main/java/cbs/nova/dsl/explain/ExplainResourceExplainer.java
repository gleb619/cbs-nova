package cbs.nova.dsl.explain;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.explain.ExplainResourceFrontmatter;
import java.util.Optional;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExplainResourceExplainer {

  public static @NonNull Function<Context<?>, Result<ExplainReport>> viaResource(
          @NonNull String name, @NonNull String resourcePath) {
    return ctx -> {
      ExplainReport explainReport;
      try {
        explainReport = parseExplainReport(name, resourcePath, ctx);
      } catch (RuntimeException ex) {
        return Result.failure(new IllegalStateException(
            "Unable to load explain resource for '%s' from classpath: %s%s".formatted(
                name, ClasspathExplainResourceResolver.DEFAULT_PREFIX, resourcePath), ex));
      }
      return Result.success(explainReport);
    };
  }

  public static Optional<ExplainReport> parseExplainReport(String name, String resourcePath, Context<?> ctx) {
    var markdown = ctx.bean(ExplainResourceResolver.class).load(resourcePath);
    var parsed = ExplainResourceFrontmatter.parse(markdown);
    var description = parsed.metadata().getOrDefault("description", "");
    return Optional.of(ExplainReport.builder()
        .name(name)
        .description(description)
        .markdown(parsed.body())
        .build());
  }
}
