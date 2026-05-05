package cbs.nova.registry;

import cbs.dsl.api.DslComponentResolver;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Spring-aware implementation of {@link DslComponentResolver}.
 *
 * <p>Delegates to {@link ApplicationContext#getBean(Class)} so that components annotated with
 * {@code @DslComponent(componentModel = SPRING)} (or resolved to it by {@code AUTO}) are obtained
 * as fully-managed Spring beans, supporting dependency injection, AOP proxies, and transaction
 * boundaries.
 */
@Component
public class SpringDslComponentResolver implements DslComponentResolver {

  private final ApplicationContext applicationContext;

  public SpringDslComponentResolver(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @Override
  public <T> T resolve(Class<T> type) {
    return applicationContext.getBean(type);
  }
}
