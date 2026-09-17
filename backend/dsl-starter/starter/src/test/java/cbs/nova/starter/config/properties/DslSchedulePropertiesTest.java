package cbs.nova.starter.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

class DslSchedulePropertiesTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withUserConfiguration(DslSchedulePropertiesConfiguration.class);

  @Test
  void defaultsAreApplied() {
    runner.run(ctx -> {
      DslScheduleProperties properties = ctx.getBean(DslScheduleProperties.class);
      assertThat(properties.catchupWindow()).isEqualTo(Duration.ofMinutes(1));
    });
  }

  @Test
  void customCatchupWindowIsBound() {
    runner
            .withPropertyValues("cbs.nova.schedule.catchup-window=30m")
            .run(ctx -> {
              DslScheduleProperties properties = ctx.getBean(DslScheduleProperties.class);
              assertThat(properties.catchupWindow()).isEqualTo(Duration.ofMinutes(30));
            });
  }

  @Configuration
  @EnableConfigurationProperties(DslScheduleProperties.class)
  static class DslSchedulePropertiesConfiguration {
  }
}
