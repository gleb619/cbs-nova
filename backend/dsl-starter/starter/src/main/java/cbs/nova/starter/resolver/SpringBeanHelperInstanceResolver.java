package cbs.nova.starter.resolver;

import cbs.nova.dsl.Executable;
import cbs.nova.dsl.helper.HelperInstanceResolver;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

@RequiredArgsConstructor
public final class SpringBeanHelperInstanceResolver implements HelperInstanceResolver {

  private final ApplicationContext applicationContext;

  @Override
  public @NonNull Executable<?, ?> resolve(@NonNull Class<?> helperClass) {
    try {
      return (Executable<?, ?>) applicationContext.getBean(helperClass);
    } catch (NoSuchBeanDefinitionException e) {
      throw new IllegalStateException(
              "Helper is not registered as a Spring bean: " + helperClass.getName(), e);
    }
  }
}
