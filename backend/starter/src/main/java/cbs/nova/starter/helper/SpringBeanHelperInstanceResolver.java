package cbs.nova.starter.helper;

import cbs.nova.dsl.Executable;
import cbs.nova.dsl.HelperInstanceResolver;
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
      return newInstance(helperClass);
    }
  }

  @SuppressWarnings("unchecked")
  @Deprecated(forRemoval = true)
  private static @NonNull Executable<?, ?> newInstance(@NonNull Class<?> helperClass) {
    try {
      // TODO: remove reflection, use typed info instead
      return (Executable<?, ?>) helperClass.getDeclaredConstructor().newInstance();
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(
              "Helper class must declare a public no-arg constructor: " + helperClass.getName(), e);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to create helper instance: " + helperClass.getName(),
              e);
    }
  }
}
