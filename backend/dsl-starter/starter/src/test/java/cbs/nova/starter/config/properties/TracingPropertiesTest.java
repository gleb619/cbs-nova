package cbs.nova.starter.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class TracingPropertiesTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withUserConfiguration(TracingPropertiesConfiguration.class);

  @Test
  void defaultsAreApplied() {
    runner.run(ctx -> {
      TracingProperties properties = ctx.getBean(TracingProperties.class);
      assertThat(properties.serviceName()).isEqualTo("cbs-nova");
      assertThat(properties.otlp().endpoint()).isEmpty();
    });
  }

  @Test
  void customValuesAreBound() {
    runner
            .withPropertyValues(
                    "cbs.nova.tracing.service-name=test-service",
                    "cbs.nova.tracing.otlp.endpoint=http://collector:4318/v1/traces")
            .run(ctx -> {
              TracingProperties properties = ctx.getBean(TracingProperties.class);
              assertThat(properties.serviceName()).isEqualTo("test-service");
              assertThat(properties.otlp().endpoint())
                      .isEqualTo("http://collector:4318/v1/traces");
            });
  }

  @Test
  void kebabCaseBindingWorks() {
    runner
            .withPropertyValues(
                    "cbs.nova.tracing.service-name=kebab-service",
                    "cbs.nova.tracing.otlp.endpoint=http://kebab:4318/v1/traces")
            .run(ctx -> {
              TracingProperties properties = ctx.getBean(TracingProperties.class);
              assertThat(properties.serviceName()).isEqualTo("kebab-service");
              assertThat(properties.otlp().endpoint()).isEqualTo("http://kebab:4318/v1/traces");
            });
  }

  @Test
  void blankServiceNameFallsBackToDefault() {
    runner
            .withPropertyValues("cbs.nova.tracing.service-name=")
            .run(ctx -> {
              TracingProperties properties = ctx.getBean(TracingProperties.class);
              assertThat(properties.serviceName()).isEqualTo("cbs-nova");
            });
  }

  @Configuration
  @EnableConfigurationProperties(TracingProperties.class)
  static class TracingPropertiesConfiguration {
  }
}
