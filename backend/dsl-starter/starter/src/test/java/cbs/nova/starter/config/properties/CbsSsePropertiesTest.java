package cbs.nova.starter.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Binding contract for {@link CbsSseProperties}: default {@code max-subscribers = 500}, override
 * via {@code cbs.sse.max-subscribers}, and invalid values (zero/negative) are clamped to the
 * default rather than blowing up at startup (so a typo in {@code application.yml} does not take the
 * starter down).
 */
class CbsSsePropertiesTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withUserConfiguration(EnableConfig.class);

  @Test
  void defaultIs500WhenNoPropertySet() {
    runner.run(ctx -> {
      assertThat(ctx).hasNotFailed();
      CbsSseProperties props = ctx.getBean(CbsSseProperties.class);
      assertThat(props.maxSubscribers()).isEqualTo(500);
    });
  }

  @Test
  void overrideIsApplied() {
    runner.withPropertyValues("cbs.sse.max-subscribers=42").run(ctx -> {
      assertThat(ctx).hasNotFailed();
      CbsSseProperties props = ctx.getBean(CbsSseProperties.class);
      assertThat(props.maxSubscribers()).isEqualTo(42);
    });
  }

  @Test
  void invalidZeroIsClampedToDefault() {
    runner.withPropertyValues("cbs.sse.max-subscribers=0").run(ctx -> {
      assertThat(ctx).hasNotFailed();
      CbsSseProperties props = ctx.getBean(CbsSseProperties.class);
      assertThat(props.maxSubscribers())
              .as("@Min(1) on record + compact ctor clamp both reject it, so we fall back to default")
              .isEqualTo(500);
    });
  }

  @Test
  void invalidNegativeIsClampedToDefault() {
    runner.withPropertyValues("cbs.sse.max-subscribers=-10").run(ctx -> {
      assertThat(ctx).hasNotFailed();
      CbsSseProperties props = ctx.getBean(CbsSseProperties.class);
      assertThat(props.maxSubscribers()).isEqualTo(500);
    });
  }

  @EnableConfigurationProperties(CbsSseProperties.class)
  static class EnableConfig {
  }
}
