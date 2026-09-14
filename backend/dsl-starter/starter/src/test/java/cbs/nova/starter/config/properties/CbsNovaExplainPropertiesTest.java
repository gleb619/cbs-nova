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
      assertThat(properties.nameMaxTokens()).isEqualTo(128);
      assertThat(properties.descriptionMaxTokens()).isEqualTo(256);
      assertThat(properties.mermaidMaxTokens()).isEqualTo(4096);
    });
  }

  @Test
  void customValuesAreBound() {
    runner
            .withPropertyValues(
                    "cbs.nova.explain.budget-chars=128",
                    "cbs.nova.explain.resources-prefix=custom-docs/",
                    "cbs.nova.explain.name-max-tokens=16",
                    "cbs.nova.explain.description-max-tokens=32",
                    "cbs.nova.explain.mermaid-max-tokens=64")
            .run(ctx -> {
              CbsNovaExplainProperties properties = ctx.getBean(CbsNovaExplainProperties.class);
              assertThat(properties.budgetChars()).isEqualTo(128);
              assertThat(properties.resourcesPrefix()).isEqualTo("custom-docs/");
              assertThat(properties.nameMaxTokens()).isEqualTo(16);
              assertThat(properties.descriptionMaxTokens()).isEqualTo(32);
              assertThat(properties.mermaidMaxTokens()).isEqualTo(64);
            });
  }

  @Test
  void negativeTokenLimitsAreClampedToZero() {
    var properties = new CbsNovaExplainProperties(4000, "explain/", -1, -2, -3);

    assertThat(properties.nameMaxTokens()).isZero();
    assertThat(properties.descriptionMaxTokens()).isZero();
    assertThat(properties.mermaidMaxTokens()).isZero();
  }

  @Test
  void blankResourcesPrefixIsRejected() {
    assertThatThrownBy(() -> new CbsNovaExplainProperties(4000, "  ", 128, 256, 4096))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("resources-prefix");
  }

  @Configuration
  @EnableConfigurationProperties(CbsNovaExplainProperties.class)
  static class ExplainPropertiesConfiguration {
  }
}
