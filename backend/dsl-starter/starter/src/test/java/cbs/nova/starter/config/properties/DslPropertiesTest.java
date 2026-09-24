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
      assertThat(properties.reload().enabled()).isFalse();
      assertThat(properties.workbenchWorkspaceRoot()).isEqualTo(".dsl-workspace");
      assertThat(properties.files().enabled()).isTrue();
      assertThat(properties.files().flushIntervalSeconds()).isEqualTo(5);
      assertThat(properties.files().maxQueueSize()).isEqualTo(100);
      assertThat(properties.files().readBulkheadPermits()).isEqualTo(32);
      assertThat(properties.files().writeBulkheadPermits()).isEqualTo(8);
      assertThat(properties.files().acquireTimeoutSeconds()).isEqualTo(5L);
      assertThat(properties.git().enabled()).isTrue();
      assertThat(properties.git().statusCacheTtlSeconds()).isEqualTo(5);
      assertThat(properties.fileBuffer().maxEntries()).isEqualTo(1000);
      assertThat(properties.fileBuffer().expireAfterWriteSeconds()).isEqualTo(3600L);
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
                    "cbs.dsl.sourceDir=/tmp/dsl-camel",
                    "cbs.dsl.task-queue=kebab-queue",
                    "cbs.dsl.worker.enabled=true",
                    "cbs.dsl.reload.enabled=false")
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
