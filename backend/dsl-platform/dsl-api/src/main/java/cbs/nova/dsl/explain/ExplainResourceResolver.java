package cbs.nova.dsl.explain;

import org.jspecify.annotations.NonNull;

@FunctionalInterface
public interface ExplainResourceResolver {

  @NonNull
  String load(@NonNull String normalizedPath);
}
