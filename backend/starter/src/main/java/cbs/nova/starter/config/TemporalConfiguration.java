package cbs.nova.starter.config;

import cbs.nova.dsl.DslRunRepository;
import cbs.nova.dsl.TemporalProcessLauncher;
import cbs.nova.dsl.TransactionInvoker;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.logging.DryRunLoggingContext;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import cbs.nova.starter.DevDslRuntime;
import cbs.nova.starter.cache.PreviewResultCache;
import cbs.nova.starter.config.properties.DryRunProperties;
import cbs.nova.starter.core.listener.DslExecutionEventBus;
import cbs.nova.starter.core.pipe.ExplainDslPipe;
import cbs.nova.starter.core.pipe.PreviewDslPipe;
import cbs.nova.starter.core.pipe.RunDslPipe;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import cbs.nova.starter.core.recorder.RunScopedExternalCallRecorder;
import cbs.nova.starter.logging.DryRunLogBufferRegistry;
import cbs.nova.starter.logging.DryRunLoggingContextPropagator;
import cbs.nova.starter.services.TemporalDslProcessLauncher;
import cbs.nova.starter.services.TemporalDslProcessService;
import cbs.nova.starter.services.TemporalDslService;
import cbs.nova.starter.services.TemporalTransactionInvoker;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@AutoConfiguration
@AutoConfigureAfter(DryRunLoggingAutoConfiguration.class)
@EnableConfigurationProperties(CbsNovaPreviewProperties.class)
public class TemporalConfiguration {

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
          DryRunLoggingContextPropagator dryRunLoggingContextPropagator) {
    WorkflowClientOptions options = WorkflowClientOptions.newBuilder()
            .setContextPropagators(List.of(dryRunLoggingContextPropagator))
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
          CbsNovaPreviewProperties previewProperties) {
    return new PreviewDslPipe(externalCallRecorder, contextFactory, dryRunLoggingContext,
            bufferRegistry, dryRunProperties.log().maxEventsPerRun(), previewResultCache,
            previewProperties);
  }

  @Bean
  @ConditionalOnMissingBean
  RunDslPipe runDslPipe(ContextFactory contextFactory) {
    return new RunDslPipe(contextFactory);
  }

  @Bean
  @ConditionalOnMissingBean
  ExplainDslPipe explainDslPipe(
          ExternalCallRecorder externalCallRecorder,
          ContextFactory contextFactory,
          DryRunLoggingContext dryRunLoggingContext,
          DryRunLogBufferRegistry bufferRegistry,
          DryRunProperties dryRunProperties,
          CbsNovaPreviewProperties previewProperties) {
    return new ExplainDslPipe(externalCallRecorder, contextFactory, dryRunLoggingContext,
            bufferRegistry, dryRunProperties.log().maxEventsPerRun(), previewProperties);
  }

  @Bean
  @ConditionalOnMissingBean
  DevDslRuntime devDslRuntime(
          PreviewDslPipe previewDslPipe,
          RunDslPipe runDslPipe,
          ExplainDslPipe explainDslPipe) {
    return new DevDslRuntime(previewDslPipe, runDslPipe, explainDslPipe);
  }

  /**
   * Custom Spring-managed executor used for asynchronous DSL process work (DB saves, workflow
   * supervision, completion bookkeeping). Replaces raw {@code ForkJoinPool.commonPool()} so that we
   * get a bounded, named thread pool under Spring's lifecycle control. The supplied
   * {@link TaskDecorator} copies the MDC, Sentry tags and OpenTelemetry context from the submitting
   * thread into the worker thread for every task, mirroring the manual {@code propagateRunId}
   * helper used today.
   */
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

  /**
   * Single-thread scheduled executor that powers the run healthcheck. The actual scheduling /
   * staleness threshold lives inside {@code TemporalDslProcessService}; this bean only owns the
   * thread so it can be shut down cleanly with the Spring context.
   */
  @Bean(name = "cbsNovaDslProcessHealthcheckExecutor", destroyMethod = "shutdownNow")
  @ConditionalOnMissingBean(name = "cbsNovaDslProcessHealthcheckExecutor")
  ScheduledExecutorService cbsNovaDslProcessHealthcheckExecutor() {
    return Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "cbs-nova-dsl-healthcheck");
      t.setDaemon(true);
      return t;
    });
  }

  /**
   * Spring {@link TaskDecorator} that re-applies the submitting thread's MDC and OpenTelemetry /
   * Sentry context to the worker thread. Tasks that do not capture context (no MDC, no active OTel
   * span, no Sentry tags) run through unchanged. Used by {@link #cbsNovaDslProcessExecutor} so that
   * {@code TemporalDslProcessService}'s async DB-save and workflow-completion tasks keep the
   * {@code runId} correlation key across boundaries.
   */
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

  @Bean
  TemporalDslProcessService temporalDslProcessService(
          ContextFactory contextFactory,
          DslRunRepository runRepository,
          JsonMapper jsonMapper,
          @Qualifier("cbsNovaDslProcessExecutor") ThreadPoolTaskExecutor dslProcessExecutor,
          @Qualifier("cbsNovaDslProcessHealthcheckExecutor") ScheduledExecutorService healthcheckExecutor,
          @Value("${cbs.nova.process.healthcheck.interval:PT30S}") Duration healthcheckInterval,
          @Value("${cbs.nova.process.healthcheck.stale-threshold:PT5M}") Duration staleThreshold,
          @Value("${cbs.nova.process.async-db-save:true}") boolean asyncDbSave) {
    return new TemporalDslProcessService(contextFactory, runRepository,
            JsonMapper.builder().build(),
            dslProcessExecutor, healthcheckExecutor,
            healthcheckInterval, staleThreshold, asyncDbSave);
  }

  @Bean
  TemporalDslService temporalDslService(WorkflowClient workflowClient) {
    return new TemporalDslService(workflowClient);
  }

}
