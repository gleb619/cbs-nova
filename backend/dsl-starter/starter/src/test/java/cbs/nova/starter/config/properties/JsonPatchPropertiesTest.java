package cbs.nova.starter.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class JsonPatchPropertiesTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withUserConfiguration(JsonPatchPropertiesConfiguration.class);

  @Test
  void defaultsAreApplied() {
    runner.run(ctx -> {
      JsonPatchProperties properties = ctx.getBean(JsonPatchProperties.class);
      assertThat(properties.prettyPrint()).isFalse();
    });
  }

  @Test
  void customValuesAreBound() {
    runner
            .withPropertyValues("cbs.dsl.helper.json-patch.pretty-print=true")
            .run(ctx -> {
              JsonPatchProperties properties = ctx.getBean(JsonPatchProperties.class);
              assertThat(properties.prettyPrint()).isTrue();
            });
  }

  @Configuration
  @EnableConfigurationProperties(JsonPatchProperties.class)
  static class JsonPatchPropertiesConfiguration {
  }
}
