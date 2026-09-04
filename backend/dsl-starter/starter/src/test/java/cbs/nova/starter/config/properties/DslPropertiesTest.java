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
      assertThat(properties.getSourceDir()).isNull();
      assertThat(properties.getTaskQueue()).isEqualTo("dsl-task-queue");
      assertThat(properties.getWorker().isEnabled()).isFalse();
      assertThat(properties.getReload().isEnabled()).isFalse();
    });
  }

  @Test
  void customValuesAreBound() {
    runner
            .withPropertyValues(
                    "csb.dsl.source-dir=/tmp/dsl",
                    "csb.dsl.task-queue=custom-queue",
                    "csb.dsl.worker.enabled=true",
                    "csb.dsl.reload.enabled=false")
            .run(ctx -> {
              DslProperties properties = ctx.getBean(DslProperties.class);
              assertThat(properties.getSourceDir()).isEqualTo("/tmp/dsl");
              assertThat(properties.getTaskQueue()).isEqualTo("custom-queue");
              assertThat(properties.getWorker().isEnabled()).isTrue();
              assertThat(properties.getReload().isEnabled()).isFalse();
            });
  }

  @Test
  void kebabCaseAndCamelCaseAreEquivalent() {
    runner
            .withPropertyValues(
                    "csb.dsl.sourceDir=/tmp/dsl-camel",
                    "csb.dsl.task-queue=kebab-queue",
                    "csb.dsl.worker.enabled=true",
                    "csb.dsl.reload.enabled=false")
            .run(ctx -> {
              DslProperties properties = ctx.getBean(DslProperties.class);
              assertThat(properties.getSourceDir()).isEqualTo("/tmp/dsl-camel");
              assertThat(properties.getTaskQueue()).isEqualTo("kebab-queue");
              assertThat(properties.getWorker().isEnabled()).isTrue();
              assertThat(properties.getReload().isEnabled()).isFalse();
            });
  }

  @Configuration
  @EnableConfigurationProperties(DslProperties.class)
  static class DslPropertiesConfiguration {
  }
}
