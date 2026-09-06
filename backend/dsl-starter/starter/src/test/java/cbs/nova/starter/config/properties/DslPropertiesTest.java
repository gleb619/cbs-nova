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
      assertThat(properties.getWorkbenchWorkspaceRoot()).isEqualTo(".workbench/drafts-fs");
      assertThat(properties.getFiles().getAcquireTimeoutSeconds()).isEqualTo(5L);
    });
  }

  @Test
  void customValuesAreBound() {
    runner
            .withPropertyValues(
                    "cbs.dsl.source-dir=/tmp/dsl",
                    "cbs.dsl.task-queue=custom-queue",
                    "cbs.dsl.worker.enabled=true",
                    "cbs.dsl.reload.enabled=false")
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
                    "cbs.dsl.sourceDir=/tmp/dsl-camel",
                    "cbs.dsl.task-queue=kebab-queue",
                    "cbs.dsl.worker.enabled=true",
                    "cbs.dsl.reload.enabled=false")
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
