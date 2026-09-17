package cbs.nova.starter.resolver;

import cbs.nova.dsl.BeanResolver;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

@RequiredArgsConstructor
public final class SpringBeanResolver implements BeanResolver {

  private final ApplicationContext applicationContext;

  private final Cache<Class<?>, Object> resolvedBeanCache = Caffeine.newBuilder()
          .maximumSize(256L)
          .build();

  @Override
  public @NonNull Object resolve(@NonNull Class<?> type) {
    return resolvedBeanCache.get(type, this::resolveFromContext);
  }

  private @NonNull Object resolveFromContext(Class<?> type) {
    try {
      return applicationContext.getBean(type);
    } catch (NoSuchBeanDefinitionException ex) {
      throw new IllegalStateException(
              "Bean not registered in Spring context: " + type.getName(), ex);
    }
  }
}
