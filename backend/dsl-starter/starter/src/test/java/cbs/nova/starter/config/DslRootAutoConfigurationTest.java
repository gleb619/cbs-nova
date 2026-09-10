package cbs.nova.starter.config;

import cbs.nova.starter.config.router.DslRouterConfiguration;
import cbs.nova.starter.security.RbacFilterConfiguration;
import cbs.nova.starter.webhook.WebhookConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotationUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

/**
 * Verifies the starter advertises exactly one auto-configuration — the root — that aggregates the
 * rest via {@link Import}. {@link DslRunRepositoryConfiguration} is imported by the root but
 * carries no {@code @Configuration} stereotype, so its {@code @ConditionalOnBean(DataSource)} beans
 * are only evaluated in the auto-configuration phase, after {@code DataSourceAutoConfiguration},
 * and never via component scanning.
 */
class DslRootAutoConfigurationTest {

  @Test
  void importsFileAdvertisesExactlyOneAutoConfiguration() throws IOException {
    var imports = ClassLoader.getSystemResourceAsStream(
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
    assertThat(imports).as("auto-configuration imports file must exist").isNotNull();
    try (var reader = new BufferedReader(new InputStreamReader(imports, StandardCharsets.UTF_8))) {
      assertThat(reader.lines().map(String::trim).filter(line -> !line.isEmpty()))
              .containsExactly(DslRootAutoConfiguration.class.getName());
    }
  }

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
            RbacFilterConfiguration.class,
            DslRunRetentionConfiguration.class,
            DslRunReconciliationConfiguration.class,
            DslMaintenanceConfiguration.class,
            DslRunRepositoryConfiguration.class,
            SecurityConfiguration.class,
            ApiKeyAuthMisconfigurationWarning.class,
            DslHealthIndicatorConfiguration.class,
            BuilderClientConfiguration.class);
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
