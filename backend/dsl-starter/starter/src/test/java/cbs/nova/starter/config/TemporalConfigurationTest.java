package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.logging.DryRunLoggingContext;
import cbs.nova.starter.DevDslRuntime;
import cbs.nova.starter.config.properties.CbsHealthProperties;
import cbs.nova.starter.config.properties.DryRunProperties;
import cbs.nova.starter.converter.MapInputConverter;
import cbs.nova.starter.core.listener.DslExecutionEventBus;
import cbs.nova.starter.core.pipe.ExplainDslPipe;
import cbs.nova.starter.core.pipe.HierarchyDslPipe;
import cbs.nova.starter.core.pipe.PreviewDslPipe;
import cbs.nova.starter.core.pipe.RunDslPipe;
import cbs.nova.starter.core.pipe.RunScopedFakeConfig;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import cbs.nova.starter.logging.DryRunLogBufferRegistry;
import cbs.nova.starter.logging.DryRunLoggingContextPropagator;
import cbs.nova.starter.reporting.HierarchyDiagramRenderer;
import cbs.nova.starter.security.ManifestObjectGuard;
import cbs.nova.starter.service.DslRunCancellationService;
import cbs.nova.starter.service.PreviewResultCache;
import cbs.nova.starter.service.RunIdentityResolver;
import cbs.nova.starter.service.TemporalDslProcessService;
import cbs.nova.starter.service.TemporalDslService;
import cbs.nova.starter.service.TemporalHealthProbe;
import cbs.nova.dsl.process.TemporalProcessLauncher;
import cbs.nova.starter.service.TemporalTransactionInvoker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.WorkerFactory;
import io.avaje.jsonb.Jsonb;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.convert.ConversionService;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wiring spec for {@link TemporalConfiguration}. Uses stub service beans so no live Temporal server
 * is required.
 */
class TemporalConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(TemporalConfiguration.class))
          .withUserConfiguration(SupportConfiguration.class);

  @Test
  void mainBeanSurfaceIsWired() {
    runner.run(ctx -> {
      assertThat(ctx).hasSingleBean(WorkflowClient.class);
      assertThat(ctx).hasSingleBean(WorkerFactory.class);
      assertThat(ctx).hasSingleBean(WorkflowServiceStubs.class);

      assertThat(ctx).hasSingleBean(TemporalProcessLauncher.class);
      assertThat(ctx).hasSingleBean(TemporalTransactionInvoker.class);
      assertThat(ctx).hasSingleBean(DslExecutionEventBus.class);
      assertThat(ctx).hasSingleBean(ExternalCallRecorder.class);
      assertThat(ctx).hasSingleBean(RunScopedFakeConfig.class);

      assertThat(ctx).hasSingleBean(PreviewDslPipe.class);
      assertThat(ctx).hasSingleBean(RunDslPipe.class);
      assertThat(ctx).hasSingleBean(HierarchyDslPipe.class);
      assertThat(ctx).hasSingleBean(ExplainDslPipe.class);
      assertThat(ctx).hasSingleBean(DevDslRuntime.class);

      assertThat(ctx).hasBean("cbsNovaPreviewDispatchExecutor");
      assertThat(ctx).hasBean("cbsNovaDslProcessExecutor");
      assertThat(ctx).hasBean("cbsNovaDslProcessHealthcheckExecutor");
      assertThat(ctx).hasSingleBean(TaskDecorator.class);

      assertThat(ctx).hasSingleBean(TemporalDslProcessService.class);
      assertThat(ctx).hasSingleBean(TemporalDslService.class);
      assertThat(ctx).hasSingleBean(DslRunCancellationService.class);
      assertThat(ctx).hasSingleBean(TemporalHealthProbe.class);
      assertThat(ctx).hasSingleBean(DryRunLoggingContextPropagator.class);
    });
  }

  @Test
  void workerRegistrationRunnerIsAbsentByDefault() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(ApplicationRunner.class));
  }

  @Test
  void workerRegistrationRunnerIsWiredWhenEnabled() {
    runner
            .withPropertyValues("cbs.dsl.worker.enabled=true")
            .run(ctx -> assertThat(ctx).hasSingleBean(ApplicationRunner.class));
  }

  @Test
  void workerRegistrationRunnerBacksOffWhenDslWorkerFactoryBeanPresent() {
    runner
            .withUserConfiguration(DslWorkerFactoryBeanConfiguration.class)
            .withPropertyValues("cbs.dsl.worker.enabled=true")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(ApplicationRunner.class));
  }

  @Test
  void guardBeanDoesNotBreakPipeWiring() {
    runner
            .withUserConfiguration(GuardConfiguration.class)
            .run(ctx -> {
              assertThat(ctx).hasSingleBean(ManifestObjectGuard.class);
              assertThat(ctx).hasSingleBean(DevDslRuntime.class);
            });
  }

  @Configuration
  @EnableConfigurationProperties({DryRunProperties.class, CbsHealthProperties.class})
  static class SupportConfiguration {

    @Bean
    WorkflowClient workflowClient() {
      return mock(WorkflowClient.class);
    }

    @Bean
    WorkerFactory workerFactory() {
      return mock(WorkerFactory.class);
    }

    @Bean
    WorkflowServiceStubs workflowServiceStubs() {
      return mock(WorkflowServiceStubs.class);
    }

    @Bean
    OpenTelemetry openTelemetry() {
      return OpenTelemetry.noop();
    }

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    DryRunLoggingContext dryRunLoggingContext() {
      return mock(DryRunLoggingContext.class);
    }

    @Bean
    DryRunLogBufferRegistry dryRunLogBufferRegistry() {
      return mock(DryRunLogBufferRegistry.class);
    }

    @Bean
    PreviewResultCache previewResultCache() {
      return mock(PreviewResultCache.class);
    }

    @Bean
    HierarchyDiagramRenderer hierarchyDiagramRenderer() {
      return mock(HierarchyDiagramRenderer.class);
    }

    @Bean
    RunIdentityResolver runIdentityResolver() {
      return mock(RunIdentityResolver.class);
    }

    @Bean
    DslRunRepository dslRunRepository() {
      return mock(DslRunRepository.class);
    }

    @Bean
    ConversionService conversionService() {
      return ApplicationConversionService.getSharedInstance();
    }

    @Bean
    JsonMapper jsonMapper() {
      return JsonMapper.builder().build();
    }

    @Bean
    MapInputConverter mapInputConverter() {
      return new MapInputConverter(Jsonb.builder().build(), new ObjectMapper());
    }
  }

  @Configuration
  static class DslWorkerFactoryBeanConfiguration {

    @Bean(name = "dslWorkerFactory")
    Object dslWorkerFactory() {
      return new Object();
    }
  }

  @Configuration
  static class GuardConfiguration {

    @Bean
    ManifestObjectGuard manifestObjectGuard() {
      return mock(ManifestObjectGuard.class);
    }
  }
}
