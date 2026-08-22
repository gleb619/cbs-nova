package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.preview.MessagingCallCaptureAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/**
 * Verifies the starter advertises a single root autoconfiguration that aggregates the rest via
 * {@link Import}, so the published artifact exposes one entry point in the autoconfiguration
 * imports file.
 */
class DslRootAutoConfigurationTest {

  @Test
  void importsFileListsOnlySingleRoot() throws IOException {
    var resource = new ClassPathResource(
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
    try (var in = Objects.requireNonNull(resource.getInputStream())) {
      var content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      var lines = content.lines()
              .map(String::trim)
              .filter(line -> !line.isEmpty() && !line.startsWith("#"))
              .toList();
      assertThat(lines)
              .as("AutoConfiguration.imports must list exactly the root autoconfiguration")
              .containsExactly(DslRootAutoConfiguration.class.getName());
    }
  }

  @Test
  void rootImportsEverySubAutoConfiguration() {
    var importAnnotation = DslRootAutoConfiguration.class.getAnnotation(Import.class);
    assertThat(importAnnotation).as("root must declare @Import").isNotNull();
    var imported = Set.of(importAnnotation.value());
    assertThat(imported).containsExactlyInAnyOrder(
            DryRunLoggingAutoConfiguration.class,
            TemporalConfiguration.class,
            DslAutoConfiguration.class,
            DslWorkerConfiguration.class,
            DslRunRepositoryConfiguration.class,
            DataSourceCallAutoConfiguration.class,
            FeignCallAutoConfiguration.class,
            PreviewAutoConfiguration.class,
            PreviewCacheAutoConfiguration.class,
            MessagingCallCaptureAutoConfiguration.class,
            PreviewMetricsAutoConfiguration.class,
            DslReloadRouterConfiguration.class,
            DslIntrospectionRouterConfiguration.class,
            DslErrorHandlingAutoConfiguration.class,
            SpringHelperAutoConfiguration.class);
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
