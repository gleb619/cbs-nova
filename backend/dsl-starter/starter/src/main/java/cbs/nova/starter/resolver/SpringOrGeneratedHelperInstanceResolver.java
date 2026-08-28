package cbs.nova.starter.resolver;

import cbs.nova.dsl.Executable;
import cbs.nova.dsl.helper.HelperInstanceResolver;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

import java.util.List;

@RequiredArgsConstructor
public final class SpringOrGeneratedHelperInstanceResolver implements HelperInstanceResolver {

  private final HelperInstanceResolver springResolver;
  private final @NonNull List<HelperInstanceResolver> generatedFactories;

  @Override
  public @NonNull Executable<?, ?> resolve(@NonNull Class<?> helperClass) {
    try {
      return springResolver.resolve(helperClass);
    } catch (IllegalStateException e) {
      if (e.getCause() instanceof NoSuchBeanDefinitionException) {
        return instantiateFromGeneratedFactory(helperClass, e);
      }
      throw e;
    }
  }

  private @NonNull Executable<?, ?> instantiateFromGeneratedFactory(@NonNull Class<?> helperClass,
          IllegalStateException originalSpringException) {
    // TODO: add Caffeine cache, with some properties config for ttl, cache instance, but not for
    // long
    for (var factory : generatedFactories) {
      try {
        return factory.resolve(helperClass);
      } catch (IllegalStateException ignored) {
        // try the next generated factory
      }
    }
    throw new IllegalStateException(
            "Helper is not a Spring bean and no generated factory can instantiate it: "
                    + helperClass.getName(),
            originalSpringException);
  }
}
