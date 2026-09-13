package cbs.nova.starter.resolver;

import cbs.nova.dsl.explain.ClasspathExplainResourceResolver;
import cbs.nova.dsl.explain.ExplainResourceResolver;
import org.jspecify.annotations.NonNull;

public final class SpringExplainResourceResolver implements ExplainResourceResolver {

  private final ExplainResourceResolver delegate;

  public SpringExplainResourceResolver(@NonNull String resourcesPrefix) {
    this.delegate = new ClasspathExplainResourceResolver(resourcesPrefix);
  }

  @Override
  public @NonNull String load(@NonNull String normalizedPath) {
    return delegate.load(normalizedPath);
  }
}
