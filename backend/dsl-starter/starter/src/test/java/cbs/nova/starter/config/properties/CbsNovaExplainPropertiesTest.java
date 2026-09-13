package cbs.nova.starter.config.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class CbsNovaExplainPropertiesTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withUserConfiguration(ExplainPropertiesConfiguration.class);

  @Test
  void defaultsAreApplied() {
    runner.run(ctx -> {
      CbsNovaExplainProperties properties = ctx.getBean(CbsNovaExplainProperties.class);
      assertThat(properties.budgetChars()).isEqualTo(4000);
      assertThat(properties.resourcesPrefix()).isEqualTo("explain/");
    });
  }

  @Test
  void customValuesAreBound() {
    runner
            .withPropertyValues(
                    "cbs.nova.explain.budget-chars=128",
                    "cbs.nova.explain.resources-prefix=custom-docs/")
            .run(ctx -> {
              CbsNovaExplainProperties properties = ctx.getBean(CbsNovaExplainProperties.class);
              assertThat(properties.budgetChars()).isEqualTo(128);
              assertThat(properties.resourcesPrefix()).isEqualTo("custom-docs/");
            });
  }

  @Test
  void blankResourcesPrefixIsRejected() {
    assertThatThrownBy(() -> new CbsNovaExplainProperties(4000, "  "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("resources-prefix");
  }

  @Configuration
  @EnableConfigurationProperties(CbsNovaExplainProperties.class)
  static class ExplainPropertiesConfiguration {
  }
}
