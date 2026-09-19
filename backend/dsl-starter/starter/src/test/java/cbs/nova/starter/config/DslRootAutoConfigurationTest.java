package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.properties.CbsHealthProperties;
import cbs.nova.starter.config.properties.CbsNovaCacheProperties;
import cbs.nova.starter.config.properties.CbsNovaFakesProperties;
import cbs.nova.starter.config.properties.CbsNovaPreviewProperties;
import cbs.nova.starter.config.router.DslRouterConfiguration;
import cbs.nova.starter.notification.NotificationConfiguration;
import cbs.nova.starter.security.ProductionSecurityPostureValidator;
import cbs.nova.starter.security.SecurityPostureReporter;
import cbs.nova.starter.webhook.WebhookConfiguration;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotationUtils;

/**
 * Verifies the starter's auto-configuration split: the root auto-configuration aggregates the bulk
 * of the nested {@link Import} configs, while the {@code @ConditionalOnBean}-gated configs
 * ({@link DslScheduleConfiguration}, {@link DslManifestConfiguration},
 * {@link PieceGuardFilterConfiguration}, {@code DslDiagnosticsRouterConfiguration},
 * {@code DslScheduleRouterConfiguration}, {@code DslManifestRouterConfiguration}) are first-class
 * auto-configurations listed in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports} so their
 * conditions are evaluated in the auto-configuration phase, after the beans they require are
 * registered. {@link DslRunRepositoryConfiguration} is imported by the root but carries no
 * {@code @Configuration} stereotype, so its {@code @ConditionalOnBean(DataSource)} beans are only
 * evaluated in the auto-configuration phase, after {@code DataSourceAutoConfiguration}, and never
 * via component scanning.
 */
class DslRootAutoConfigurationTest {

  @Test
  void importsFileAdvertisesAutoConfigurations() throws IOException {
    var imports = ClassLoader.getSystemResourceAsStream(
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
    assertThat(imports).as("auto-configuration imports file must exist").isNotNull();
    try (var reader = new BufferedReader(new InputStreamReader(imports, StandardCharsets.UTF_8))) {
      assertThat(reader.lines().map(String::trim).filter(line -> !line.isEmpty()))
              .containsExactly(
                      DslRootAutoConfiguration.class.getName(),
                      DslScheduleConfiguration.class.getName(),
                      DslManifestConfiguration.class.getName(),
                      PieceGuardFilterConfiguration.class.getName(),
                      PieceCheckPipelineConfiguration.class.getName(),
                      "cbs.nova.starter.config.router.DslDiagnosticsRouterConfiguration",
                      "cbs.nova.starter.config.router.DslScheduleRouterConfiguration",
                      "cbs.nova.starter.config.router.DslManifestRouterConfiguration",
                      ManifestObjectGuardConfiguration.class.getName(),
                      VhsConfiguration.class.getName(),
                      "cbs.nova.starter.config.router.NotificationRouterConfiguration");
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
            DslRunRetentionConfiguration.class,
            DslRunReconciliationConfiguration.class,
            DslMaintenanceConfiguration.class,
            DslErrorHandlingConfiguration.class,
            SpringHelperConfiguration.class,
            ApiKeyAuthFilterConfiguration.class,
            RateLimitFilterConfiguration.class,
            RbacFilterConfiguration.class,
            SecurityConfiguration.class,
            ApiKeyAuthMisconfigurationWarning.class,
            ProductionSecurityPostureValidator.class,
            SecurityPostureReporter.class,
            ExplainBudgetStageWarmupConfiguration.class,
            SentryStatusReporter.class,
            DslHealthIndicatorConfiguration.class,
            BuilderClientConfiguration.class,
            DslRunRepositoryConfiguration.class,
            VhsConfiguration.class,
            NotificationConfiguration.class);
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
