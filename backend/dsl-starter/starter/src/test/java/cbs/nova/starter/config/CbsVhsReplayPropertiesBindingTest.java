package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.properties.CbsVhsReplayProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Pins that {@link CbsVhsReplayProperties} binds from configuration even though it exposes two
 * public constructors (canonical + 13-arg compatibility overload); a missing
 * {@code @ConstructorBinding} breaks context startup with "No default constructor found".
 */
class CbsVhsReplayPropertiesBindingTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withUserConfiguration(Config.class);

  @EnableConfigurationProperties(CbsVhsReplayProperties.class)
  static class Config {
  }

  @Test
  void contextStartsAndStatusLimitDefaultsTo200() {
    runner.run(ctx -> {
      assertThat(ctx).hasNotFailed();
      assertThat(ctx.getBean(CbsVhsReplayProperties.class).statusLimit()).isEqualTo(200);
    });
  }

  @Test
  void statusLimitBindsFromProperty() {
    runner.withPropertyValues("cbs.vhs.replay.status-limit=7").run(ctx -> {
      assertThat(ctx).hasNotFailed();
      assertThat(ctx.getBean(CbsVhsReplayProperties.class).statusLimit()).isEqualTo(7);
    });
  }

  @Test
  void nonPositiveStatusLimitFallsBackToDefault() {
    runner.withPropertyValues("cbs.vhs.replay.status-limit=0").run(ctx -> {
      assertThat(ctx).hasNotFailed();
      assertThat(ctx.getBean(CbsVhsReplayProperties.class).statusLimit()).isEqualTo(200);
    });
  }
}
