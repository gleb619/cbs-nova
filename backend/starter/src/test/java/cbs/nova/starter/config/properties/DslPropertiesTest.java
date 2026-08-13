package cbs.nova.starter.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class DslPropertiesTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withUserConfiguration(DslPropertiesConfiguration.class);

  @Test
  void defaultsAreApplied() {
    runner.run(ctx -> {
      DslProperties properties = ctx.getBean(DslProperties.class);
      assertThat(properties.sourceDir()).isNull();
      assertThat(properties.taskQueue()).isEqualTo("dsl-task-queue");
      assertThat(properties.worker().enabled()).isFalse();
      assertThat(properties.reload().enabled()).isTrue();
    });
  }

  @Test
  void customValuesAreBound() {
    runner
            .withPropertyValues(
                    "dsl.source-dir=/tmp/dsl",
                    "dsl.task-queue=custom-queue",
                    "dsl.worker.enabled=true",
                    "dsl.reload.enabled=false")
            .run(ctx -> {
              DslProperties properties = ctx.getBean(DslProperties.class);
              assertThat(properties.sourceDir()).isEqualTo("/tmp/dsl");
              assertThat(properties.taskQueue()).isEqualTo("custom-queue");
              assertThat(properties.worker().enabled()).isTrue();
              assertThat(properties.reload().enabled()).isFalse();
            });
  }

  @Test
  void kebabCaseAndCamelCaseAreEquivalent() {
    runner
            .withPropertyValues(
                    "dsl.sourceDir=/tmp/dsl-camel",
                    "dsl.task-queue=kebab-queue",
                    "dsl.worker.enabled=true",
                    "dsl.reload.enabled=false")
            .run(ctx -> {
              DslProperties properties = ctx.getBean(DslProperties.class);
              assertThat(properties.sourceDir()).isEqualTo("/tmp/dsl-camel");
              assertThat(properties.taskQueue()).isEqualTo("kebab-queue");
              assertThat(properties.worker().enabled()).isTrue();
              assertThat(properties.reload().enabled()).isFalse();
            });
  }

  @Configuration
  @EnableConfigurationProperties(DslProperties.class)
  static class DslPropertiesConfiguration {
  }
}
