package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.PropertyResolver;
import cbs.nova.starter.config.PropertyResolverConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PropertyResolverConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
          .withUserConfiguration(PropertyResolverConfiguration.class)
          .withPropertyValues("my.test.key=hello");

  @Test
  void beanIsCreated() {
    contextRunner.run(ctx -> assertThat(ctx).hasSingleBean(PropertyResolver.class));
  }

  @Test
  void beanResolvesSpringProperties() {
    contextRunner.run(
            ctx -> {
              var resolver = ctx.getBean(PropertyResolver.class);
              assertThat(resolver.resolve("value=${my.test.key}")).isEqualTo("value=hello");
            });
  }
}
