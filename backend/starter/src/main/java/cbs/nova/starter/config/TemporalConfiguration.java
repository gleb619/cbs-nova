package cbs.nova.starter.config;

import cbs.nova.dsl.DslRunRepository;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.TemporalProcessLauncher;
import cbs.nova.dsl.TransactionInvoker;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.logging.DryRunLoggingContext;
import cbs.nova.starter.DevDslRuntime;
import cbs.nova.starter.ExternalCallTracker;
import cbs.nova.starter.cache.PreviewResultCache;
import cbs.nova.starter.logging.DryRunLoggingContextPropagator;
import cbs.nova.starter.services.TemporalDslProcessLauncher;
import cbs.nova.starter.services.TemporalDslProcessService;
import cbs.nova.starter.services.TemporalTransactionInvoker;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@AutoConfiguration
@AutoConfigureAfter(DryRunLoggingAutoConfiguration.class)
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
  TemporalProcessLauncher temporalProcessLauncher(WorkflowClient workflowClient) {
    return new TemporalDslProcessLauncher(workflowClient, JsonMapper.builder().build());
  }

  @Bean
  @ConditionalOnMissingBean
  TemporalTransactionInvoker temporalTransactionInvoker() {
    return new TemporalTransactionInvoker();
  }

  @Bean
  @ConditionalOnMissingBean
  ExternalCallTracker externalCallTracker() {
    return new ExternalCallTracker();
  }

  @Bean
  @ConditionalOnMissingBean
  ExecutionTraceCollector executionTraceCollector() {
    return new ExecutionTraceCollector();
  }

  @Bean
  @ConditionalOnMissingBean
  ContextFactory contextFactory() {
    return new ContextFactory();
  }

  @Bean
  @ConditionalOnMissingBean
  DslRunRepository dslRunRepository() {
    return new cbs.nova.dsl.repository.InMemoryDslRunRepository();
  }

  @Bean
  @ConditionalOnMissingBean
  DevDslRuntime devDslRuntime(
          ExternalCallTracker externalCallTracker,
          ExecutionTraceCollector executionTraceCollector,
          ContextFactory contextFactory,
          DryRunLoggingContext dryRunLoggingContext,
          PreviewResultCache previewResultCache,
          @Value("${cbs.nova.preview.callTree.maxDepth:32}") int previewCallTreeMaxDepth,
          @Value("${cbs.nova.preview.cache.enabled:true}") boolean previewCacheEnabled) {
    return new DevDslRuntime(externalCallTracker, executionTraceCollector, contextFactory,
            dryRunLoggingContext, previewResultCache, previewCallTreeMaxDepth, previewCacheEnabled);
  }

  @Bean
  TemporalDslProcessService temporalDslProcessService(
          ContextFactory contextFactory,
          DslRunRepository runRepository,
          JsonMapper jsonMapper,
          ExecutionTraceCollector executionTraceCollector) {
    return new TemporalDslProcessService(contextFactory, runRepository,
            JsonMapper.builder().build(), executionTraceCollector);
  }

}
