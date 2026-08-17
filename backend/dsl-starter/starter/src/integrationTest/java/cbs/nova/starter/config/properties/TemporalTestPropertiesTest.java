package cbs.nova.starter.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class TemporalTestPropertiesTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withUserConfiguration(TemporalTestPropertiesConfiguration.class);

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void targetIsBoundFromProperty() {
    runner
            .withPropertyValues("temporal.target=localhost:7233")
            .run(ctx -> {
              TemporalTestProperties properties = ctx.getBean(TemporalTestProperties.class);
              assertThat(properties.target()).isEqualTo("localhost:7233");
            });
  }

  @Test
  void blankTargetIsRejectedByValidator() {
    var properties = new TemporalTestProperties("");
    var violations = validator.validate(properties);
    assertThat(violations).isNotEmpty();
  }

  @Configuration
  @EnableConfigurationProperties(TemporalTestProperties.class)
  static class TemporalTestPropertiesConfiguration {
  }
}
