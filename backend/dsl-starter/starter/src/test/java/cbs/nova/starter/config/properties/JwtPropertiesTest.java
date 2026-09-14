package cbs.nova.starter.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class JwtPropertiesTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withUserConfiguration(JwtPropertiesConfiguration.class);

  @Test
  void defaultsAreApplied() {
    runner.run(ctx -> {
      JwtProperties properties = ctx.getBean(JwtProperties.class);
      assertThat(properties.defaultAlgorithm()).isEqualTo("HS256");
      assertThat(properties.defaultTtlSeconds()).isEqualTo(3600L);
    });
  }

  @Test
  void customValuesAreBound() {
    runner
            .withPropertyValues(
                    "cbs.dsl.helper.jwt.default-algorithm=HS512",
                    "cbs.dsl.helper.jwt.default-ttl-seconds=7200")
            .run(ctx -> {
              JwtProperties properties = ctx.getBean(JwtProperties.class);
              assertThat(properties.defaultAlgorithm()).isEqualTo("HS512");
              assertThat(properties.defaultTtlSeconds()).isEqualTo(7200L);
            });
  }

  @Configuration
  @EnableConfigurationProperties(JwtProperties.class)
  static class JwtPropertiesConfiguration {
  }
}
