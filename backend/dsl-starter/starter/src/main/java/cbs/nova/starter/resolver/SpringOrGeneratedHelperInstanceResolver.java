package cbs.nova.starter.resolver;

import cbs.nova.dsl.Executable;
import cbs.nova.dsl.helper.HelperInstanceResolver;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

public final class SpringOrGeneratedHelperInstanceResolver implements HelperInstanceResolver {

  private static final Duration CACHE_TTL = Duration.ofMinutes(1);
  private static final long CACHE_MAX_SIZE = 1_024L;

  private final HelperInstanceResolver springResolver;
  private final @NonNull List<HelperInstanceResolver> generatedFactories;
  private final Cache<Class<?>, Executable<?, ?>> cache;

  public SpringOrGeneratedHelperInstanceResolver(
          HelperInstanceResolver springResolver,
          @NonNull List<HelperInstanceResolver> generatedFactories) {
    this(springResolver, generatedFactories, CACHE_TTL, CACHE_MAX_SIZE);
  }

  public SpringOrGeneratedHelperInstanceResolver(
          HelperInstanceResolver springResolver,
          @NonNull List<HelperInstanceResolver> generatedFactories,
          Duration ttl,
          long maxSize) {
    this.springResolver = springResolver;
    this.generatedFactories = generatedFactories;
    this.cache = Caffeine.newBuilder()
            .expireAfterWrite(ttl)
            .maximumSize(maxSize)
            .build();
  }

  @Override
  public @NonNull Executable<?, ?> resolve(@NonNull Class<?> helperClass) {
    Executable<?, ?> cached = cache.getIfPresent(helperClass);
    if (cached != null) {
      return cached;
    }
    Executable<?, ?> resolved = doResolve(helperClass);
    // Only cache successful resolutions so callers can observe later Spring registrations
    // and so transient failures are retried on the next call.
    cache.put(helperClass, resolved);
    return resolved;
  }

  private @NonNull Executable<?, ?> doResolve(@NonNull Class<?> helperClass) {
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
