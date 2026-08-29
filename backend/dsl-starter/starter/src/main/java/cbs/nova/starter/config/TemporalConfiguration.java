package cbs.nova.starter.config;

import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.GeneratedClassDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.logging.DryRunLoggingContext;
import cbs.nova.dsl.process.TemporalProcessLauncher;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import cbs.nova.starter.DevDslRuntime;
import cbs.nova.starter.service.PreviewResultCache;
import cbs.nova.starter.config.properties.DryRunProperties;
import cbs.nova.starter.config.properties.DslRunsProperties;
import cbs.nova.starter.converter.MapInputConverter;
import cbs.nova.starter.core.listener.DslExecutionEventBus;
import cbs.nova.starter.core.pipe.ExplainDslPipe;
import cbs.nova.starter.core.pipe.PreviewDslPipe;
import cbs.nova.starter.core.pipe.RunDslPipe;
import cbs.nova.starter.core.pipe.RunScopedFakeConfig;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import cbs.nova.starter.core.recorder.RunScopedExternalCallRecorder;
import cbs.nova.starter.logging.DryRunLogBufferRegistry;
import cbs.nova.starter.logging.DryRunLoggingContextPropagator;
import cbs.nova.starter.reporting.ExplainDiagramRenderer;
import cbs.nova.starter.service.DslRunCancellationService;
import cbs.nova.starter.service.TemporalDslProcessLauncher;
import cbs.nova.starter.service.TemporalDslProcessService;
import cbs.nova.starter.service.TemporalDslService;
import cbs.nova.starter.service.TemporalTransactionInvoker;
import cbs.nova.starter.tracing.OpenTelemetryContextPropagator;
import io.opentelemetry.api.OpenTelemetry;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

@Configuration
@EnableConfigurationProperties({CbsNovaPreviewProperties.class, CbsNovaFakesProperties.class,
    DslRunsProperties.class})
public class TemporalConfiguration {

  @Bean(destroyMethod = "shutdown")
  @ConditionalOnMissingBean(WorkerFactory.class)
  WorkerFactory workerFactory(WorkflowClient workflowClient) {
    return WorkerFactory.newInstance(workflowClient);
  }

  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE)
  @ConditionalOnProperty(name = "dsl.worker.enabled", havingValue = "true")
  @ConditionalOnMissingBean(name = "dslWorkerFactory")
  ApplicationRunner temporalWorkerRegistrationRunner(WorkerFactory workerFactory) {
    return args -> {
      var processes = GlobalManager.globalManager().generatedProcesses();
      if (processes.isEmpty()) {
        return;
      }

      Map<String, Set<Class<?>>> implementationsByQueue = processes.stream()
              .filter(descriptor -> descriptor.type() == DslType.PROCESS)
              .collect(Collectors.groupingBy(
                      GeneratedClassDescriptor::taskQueue,
                      Collectors.mapping(
                              GeneratedClassDescriptor::temporalImplementation,
                              Collectors.toSet())));

      implementationsByQueue.forEach((taskQueue, implementations) -> {
        Worker worker = workerFactory.newWorker(taskQueue);
        worker.registerWorkflowImplementationTypes(implementations.toArray(new Class<?>[0]));
      });

      workerFactory.start();
    };
  }

  @Bean
  @ConditionalOnMissingBean
  WorkflowServiceStubs workflowServiceStubs(
          @Value("${temporal.connection-target:127.0.0.1:7233}") String connectionTarget) {
    return WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder().setTarget(connectionTarget).build());
  }

  @Bean
  @ConditionalOnMissingBean
  WorkflowClient workflowClient(WorkflowServiceStubs workflowServiceStubs,
          DryRunLoggingContextPropagator dryRunLoggingContextPropagator,
          OpenTelemetryContextPropagator openTelemetryContextPropagator) {
    WorkflowClientOptions options = WorkflowClientOptions.newBuilder()
            .setContextPropagators(List.of(
                    dryRunLoggingContextPropagator, openTelemetryContextPropagator))
            .build();
    return WorkflowClient.newInstance(workflowServiceStubs, options);
  }

  @Bean
  @ConditionalOnMissingBean
  DryRunLoggingContextPropagator dryRunLoggingContextPropagator(
          DryRunLoggingContext dryRunLoggingContext) {
    return new DryRunLoggingContextPropagator(dryRunLoggingContext);
  }

  @Bean
  @ConditionalOnMissingBean
  TemporalProcessLauncher temporalProcessLauncher(
          WorkflowClient workflowClient,
          @Value("${temporal.execution-timeout:30s}") Duration executionTimeout,
          @Value("${temporal.task-timeout:5s}") Duration taskTimeout) {
    return new TemporalDslProcessLauncher(workflowClient, JsonMapper.builder().build(),
            executionTimeout, taskTimeout);
  }

  @Bean
  @ConditionalOnMissingBean
  TemporalTransactionInvoker temporalTransactionInvoker() {
    return new TemporalTransactionInvoker();
  }

  @Bean
  @ConditionalOnMissingBean
  DslExecutionEventBus dslExecutionEventBus() {
    return new DslExecutionEventBus();
  }

  @Bean
  @ConditionalOnMissingBean
  ExternalCallRecorder externalCallRecorder(
          @Autowired(required = false) @Nullable DslExecutionEventBus eventBus) {
    return new RunScopedExternalCallRecorder(eventBus);
  }

  @Bean
  @ConditionalOnMissingBean
  ContextFactory contextFactory() {
    return new ContextFactory();
  }

  @Bean
  @ConditionalOnMissingBean
  RunScopedFakeConfig runScopedFakeConfig() {
    return new RunScopedFakeConfig();
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnMissingClass("cbs.nova.starter.persistence.JdbcDslRunRepository")
  DslRunRepository dslRunRepository() {
    return new InMemoryDslRunRepository();
  }

  @Bean
  @ConditionalOnMissingBean
  PreviewDslPipe previewDslPipe(
          ExternalCallRecorder externalCallRecorder,
          ContextFactory contextFactory,
          DryRunLoggingContext dryRunLoggingContext,
          DryRunLogBufferRegistry bufferRegistry,
          DryRunProperties dryRunProperties,
          PreviewResultCache previewResultCache,
          CbsNovaPreviewProperties previewProperties,
          CbsNovaFakesProperties fakesProperties,
          RunScopedFakeConfig runScopedFakeConfig,
          MeterRegistry meterRegistry) {
    return new PreviewDslPipe(externalCallRecorder, contextFactory, dryRunLoggingContext,
            bufferRegistry, dryRunProperties.log().maxEventsPerRun(), previewResultCache,
            previewProperties, fakesProperties, runScopedFakeConfig, meterRegistry);
  }

  @Bean
  @ConditionalOnMissingBean
  RunDslPipe runDslPipe(
          ContextFactory contextFactory,
          ExternalCallRecorder externalCallRecorder,
          CbsNovaFakesProperties fakesProperties,
          RunScopedFakeConfig runScopedFakeConfig) {
    return new RunDslPipe(contextFactory, externalCallRecorder, fakesProperties,
            runScopedFakeConfig);
  }

  @Bean
  @ConditionalOnMissingBean
  ExplainDslPipe explainDslPipe(
          ExternalCallRecorder externalCallRecorder,
          ContextFactory contextFactory,
          DryRunLoggingContext dryRunLoggingContext,
          DryRunLogBufferRegistry bufferRegistry,
          DryRunProperties dryRunProperties,
          CbsNovaPreviewProperties previewProperties,
          CbsNovaFakesProperties fakesProperties,
          RunScopedFakeConfig runScopedFakeConfig,
          MeterRegistry meterRegistry,
          ExplainDiagramRenderer diagramRenderer) {
    return new ExplainDslPipe(externalCallRecorder, contextFactory, dryRunLoggingContext,
            bufferRegistry, dryRunProperties.log().maxEventsPerRun(), previewProperties,
            fakesProperties, runScopedFakeConfig, meterRegistry, diagramRenderer);
  }

  @Bean
  @ConditionalOnMissingBean
  DevDslRuntime devDslRuntime(
          PreviewDslPipe previewDslPipe,
          RunDslPipe runDslPipe,
          ExplainDslPipe explainDslPipe) {
    return new DevDslRuntime(previewDslPipe, runDslPipe, explainDslPipe);
  }

  @Bean(name = "cbsNovaDslProcessExecutor", destroyMethod = "shutdown")
  @ConditionalOnMissingBean(name = "cbsNovaDslProcessExecutor")
  ThreadPoolTaskExecutor cbsNovaDslProcessExecutor(
          @Value("${cbs.nova.process.executor.core-size:4}") int coreSize,
          @Value("${cbs.nova.process.executor.max-size:16}") int maxSize,
          @Value("${cbs.nova.process.executor.queue-capacity:64}") int queueCapacity,
          TaskDecorator cbsNovaDslContextDecorator) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(coreSize);
    executor.setMaxPoolSize(maxSize);
    executor.setQueueCapacity(queueCapacity);
    executor.setThreadNamePrefix("cbs-nova-dsl-");
    executor.setTaskDecorator(cbsNovaDslContextDecorator);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(15);
    executor.initialize();
    return executor;
  }

  @Bean(name = "cbsNovaDslProcessHealthcheckExecutor", destroyMethod = "shutdownNow")
  @ConditionalOnMissingBean(name = "cbsNovaDslProcessHealthcheckExecutor")
  ScheduledExecutorService cbsNovaDslProcessHealthcheckExecutor() {
    return Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "cbs-nova-dsl-healthcheck");
      t.setDaemon(true);
      return t;
    });
  }

  @Bean
  @ConditionalOnMissingBean
  TaskDecorator cbsNovaDslContextDecorator() {
    return runnable -> {
      Map<String, String> mdc = MDC.getCopyOfContextMap();
      return () -> {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        if (mdc != null) {
          MDC.setContextMap(mdc);
        } else {
          MDC.clear();
        }
        try {
          runnable.run();
        } finally {
          if (previous != null) {
            MDC.setContextMap(previous);
          } else {
            MDC.clear();
          }
        }
      };
    };
  }

  @Bean(destroyMethod = "shutdownHealthcheck")
  TemporalDslProcessService temporalDslProcessService(
          ContextFactory contextFactory,
          DslRunRepository runRepository,
          JsonMapper jsonMapper,
          @Qualifier("cbsNovaDslProcessExecutor") ThreadPoolTaskExecutor dslProcessExecutor,
          @Qualifier("cbsNovaDslProcessHealthcheckExecutor") ScheduledExecutorService healthcheckExecutor,
          @Value("${cbs.nova.process.healthcheck.interval:PT30S}") Duration healthcheckInterval,
          @Value("${cbs.nova.process.healthcheck.stale-threshold:PT5M}") Duration staleThreshold,
          @Value("${cbs.nova.process.async-db-save:true}") boolean asyncDbSave,
          DslRunsProperties dslRunsProperties,
          OpenTelemetry openTelemetry,
          MeterRegistry meterRegistry) {
    TemporalDslProcessService service = new TemporalDslProcessService(contextFactory, runRepository,
            JsonMapper.builder().build(),
            dslProcessExecutor, healthcheckExecutor,
            healthcheckInterval, staleThreshold, asyncDbSave,
            dslRunsProperties.getMaxOutputBytes(),
            meterRegistry);
    service.setOpenTelemetry(openTelemetry);
    return service;
  }

  @Bean
  TemporalDslService temporalDslService(WorkflowClient workflowClient,
          MapInputConverter mapInputConverter,
          WorkerFactory workerFactory) {
    return new TemporalDslService(workflowClient, mapInputConverter, workerFactory);
  }

  @Bean
  @ConditionalOnMissingBean
  DslRunCancellationService dslRunCancellationService(WorkflowClient workflowClient,
          DslRunRepository runRepository,
          TemporalDslProcessService temporalDslProcessService) {
    return new DslRunCancellationService(workflowClient, runRepository,
            Clock.systemUTC(), temporalDslProcessService);
  }

}
