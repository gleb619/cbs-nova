package cbs.nova.starter.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies the {@code cbs.dsl.builder-client.*} property binding shape and defaults (T498).
 */
class DslBuilderClientPropertiesTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withUserConfiguration(DslBuilderClientPropertiesConfiguration.class);

  @Test
  void defaultsAreApplied() {
    runner.run(ctx -> {
      DslBuilderClientProperties properties = ctx.getBean(DslBuilderClientProperties.class);
      assertThat(properties.enabled()).isTrue();
      assertThat(properties.baseUrl()).isEqualTo("http://localhost:8091");
      assertThat(properties.http2()).isTrue();
      assertThat(properties.queue().capacity()).isEqualTo(100);
      assertThat(properties.queue().offerTimeoutMillis()).isEqualTo(5000L);
      assertThat(properties.queue().workers()).isEqualTo(4);
      assertThat(properties.bulkhead().permits()).isEqualTo(8);
      assertThat(properties.bulkhead().acquireTimeoutSeconds()).isEqualTo(5L);
      assertThat(properties.breaker().failureThreshold()).isEqualTo(5);
      assertThat(properties.breaker().openDurationSeconds()).isEqualTo(30L);
      assertThat(properties.breaker().halfOpenProbes()).isEqualTo(3);
      assertThat(properties.timeouts().connectMillis()).isEqualTo(5000L);
      assertThat(properties.timeouts().readMillis()).isEqualTo(60000L);
    });
  }

  @Test
  void customValuesAreBound() {
    runner
            .withPropertyValues(
                    "cbs.dsl.builder-client.base-url=http://builder:8080",
                    "cbs.dsl.builder-client.queue.capacity=50",
                    "cbs.dsl.builder-client.queue.workers=8",
                    "cbs.dsl.builder-client.bulkhead.permits=16",
                    "cbs.dsl.builder-client.breaker.failure-threshold=10",
                    "cbs.dsl.builder-client.breaker.open-duration-seconds=60",
                    "cbs.dsl.builder-client.timeouts.read-millis=30000")
            .run(ctx -> {
              DslBuilderClientProperties properties = ctx.getBean(DslBuilderClientProperties.class);
              assertThat(properties.baseUrl()).isEqualTo("http://builder:8080");
              assertThat(properties.queue().capacity()).isEqualTo(50);
              assertThat(properties.queue().workers()).isEqualTo(8);
              assertThat(properties.bulkhead().permits()).isEqualTo(16);
              assertThat(properties.breaker().failureThreshold()).isEqualTo(10);
              assertThat(properties.breaker().openDurationSeconds()).isEqualTo(60L);
              assertThat(properties.timeouts().readMillis()).isEqualTo(30000L);
            });
  }

  @Configuration
  @EnableConfigurationProperties(DslBuilderClientProperties.class)
  static class DslBuilderClientPropertiesConfiguration {
  }

}
