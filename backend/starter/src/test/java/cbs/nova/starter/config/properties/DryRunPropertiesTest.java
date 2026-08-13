package cbs.nova.starter.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class DryRunPropertiesTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withUserConfiguration(DryRunPropertiesConfiguration.class);

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void defaultsAreApplied() {
    runner.run(ctx -> {
      DryRunProperties properties = ctx.getBean(DryRunProperties.class);
      assertThat(properties.context().type()).isEqualTo("threadlocal");
      assertThat(properties.log().maxEventsPerRun()).isEqualTo(1000);
    });
  }

  @Test
  void customValuesAreBound() {
    runner
            .withPropertyValues(
                    "cbs.nova.dryRun.context.type=scoped",
                    "cbs.nova.dryRun.log.maxEventsPerRun=100")
            .run(ctx -> {
              DryRunProperties properties = ctx.getBean(DryRunProperties.class);
              assertThat(properties.context().type()).isEqualTo("scoped");
              assertThat(properties.log().maxEventsPerRun()).isEqualTo(100);
            });
  }

  @Test
  void invalidMaxEventsPerRunIsRejectedByValidator() {
    var properties = new DryRunProperties(null, new DryRunProperties.Log(0));
    var violations = validator.validate(properties);
    assertThat(violations).isNotEmpty();
  }

  @Test
  void kebabCaseBindingWorks() {
    runner
            .withPropertyValues(
                    "cbs.nova.dry-run.context.type=threadlocal",
                    "cbs.nova.dry-run.log.max-events-per-run=42")
            .run(ctx -> {
              DryRunProperties properties = ctx.getBean(DryRunProperties.class);
              assertThat(properties.log().maxEventsPerRun()).isEqualTo(42);
            });
  }

  @Configuration
  @EnableConfigurationProperties(DryRunProperties.class)
  static class DryRunPropertiesConfiguration {
  }
}
