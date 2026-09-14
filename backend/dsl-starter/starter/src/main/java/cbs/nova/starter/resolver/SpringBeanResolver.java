package cbs.nova.starter.resolver;

import cbs.nova.dsl.BeanResolver;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

@RequiredArgsConstructor
public final class SpringBeanResolver implements BeanResolver {

  private final ApplicationContext applicationContext;

  // TODO: add some guard, to resolve on dsl beans. E.g. add some setting to app.yml with caffeine
  // memoize
  @Override
  public @NonNull Object resolve(@NonNull Class<?> type) {
    try {
      return applicationContext.getBean(type);
    } catch (NoSuchBeanDefinitionException ex) {
      throw new IllegalStateException(
              "Bean not registered in Spring context: " + type.getName(), ex);
    }
  }
}
