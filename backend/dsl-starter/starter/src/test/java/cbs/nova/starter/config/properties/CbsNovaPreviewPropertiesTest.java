package cbs.nova.starter.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.CbsNovaPreviewProperties;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class CbsNovaPreviewPropertiesTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withUserConfiguration(PreviewPropertiesConfiguration.class);

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void defaultsAreApplied() {
    runner.run(ctx -> {
      CbsNovaPreviewProperties properties = ctx.getBean(CbsNovaPreviewProperties.class);
      assertThat(properties.callTree().maxDepth()).isEqualTo(32);
      assertThat(properties.cache().enabled()).isTrue();
      assertThat(properties.cache().ttlMs()).isEqualTo(300000L);
    });
  }

  @Test
  void customValuesAreBound() {
    runner
            .withPropertyValues(
                    "cbs.nova.preview.callTree.maxDepth=10",
                    "cbs.nova.preview.cache.enabled=false",
                    "cbs.nova.preview.cache.ttlMs=5000")
            .run(ctx -> {
              CbsNovaPreviewProperties properties = ctx.getBean(CbsNovaPreviewProperties.class);
              assertThat(properties.callTree().maxDepth()).isEqualTo(10);
              assertThat(properties.cache().enabled()).isFalse();
              assertThat(properties.cache().ttlMs()).isEqualTo(5000L);
            });
  }

  @Test
  void negativeTtlIsRejectedByValidator() {
    var properties = new CbsNovaPreviewProperties(null,
            new CbsNovaPreviewProperties.Cache(true, -1));
    var violations = validator.validate(properties);
    assertThat(violations).isNotEmpty();
  }

  @Test
  void kebabCaseBindingWorks() {
    runner
            .withPropertyValues("cbs.nova.preview.cache.ttl-ms=1234")
            .run(ctx -> {
              CbsNovaPreviewProperties properties = ctx.getBean(CbsNovaPreviewProperties.class);
              assertThat(properties.cache().ttlMs()).isEqualTo(1234L);
            });
  }

  @Configuration
  @EnableConfigurationProperties(CbsNovaPreviewProperties.class)
  static class PreviewPropertiesConfiguration {
  }
}
