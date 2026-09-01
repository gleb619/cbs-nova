package cbs.nova.starter.config;

import cbs.nova.starter.webhook.WebhookConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotationUtils;

import java.util.Arrays;
import java.util.Set;

/**
 * Verifies the starter advertises a single root autoconfiguration that aggregates the rest via
 * {@link Import}, so the published artifact exposes one entry point in the autoconfiguration
 * imports file.
 */
class DslRootAutoConfigurationTest {

  @Test
  void rootImportsEverySubConfiguration() {
    var importAnnotation = DslRootAutoConfiguration.class.getAnnotation(Import.class);
    assertThat(importAnnotation).as("root must declare @Import").isNotNull();
    var imported = Set.of(importAnnotation.value());
    assertThat(imported).containsExactlyInAnyOrder(
            RequestIdFilterConfiguration.class,
            LoggingConfiguration.class,
            DryRunLoggingConfiguration.class,
            TemporalConfiguration.class,
            DslConfiguration.class,
            DslWorkerConfiguration.class,
            DataSourceCallConfiguration.class,
            FeignCallConfiguration.class,
            PreviewConfiguration.class,
            PreviewCacheConfiguration.class,
            MessagingCallCaptureConfiguration.class,
            PreviewMetricsConfiguration.class,
            DslRouterConfiguration.class,
            WebhookConfiguration.class,
            DslErrorHandlingConfiguration.class,
            SpringHelperConfiguration.class,
            ApiKeyAuthFilterConfiguration.class,
            RateLimitFilterConfiguration.class,
            DslRunRetentionConfiguration.class,
            DslRunReconciliationConfiguration.class);
  }

  @Test
  void temporalConfigurationBindsCbsNovaFakesProperties() {
    var enable = AnnotationUtils.findAnnotation(TemporalConfiguration.class,
            EnableConfigurationProperties.class);
    assertThat(enable).as("TemporalConfiguration must declare @EnableConfigurationProperties")
            .isNotNull();
    var bound = Arrays.asList(enable.value());
    assertThat(bound).contains(CbsNovaFakesProperties.class,
            CbsNovaPreviewProperties.class);
  }
}
