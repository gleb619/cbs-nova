package cbs.nova.starter.config;

import cbs.nova.dsl.DslRunRepository;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.TemporalProcessLauncher;
import cbs.nova.dsl.TransactionInvoker;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import cbs.nova.starter.DevDslRuntime;
import cbs.nova.starter.ExternalCallTracker;
import cbs.nova.starter.services.TemporalDslProcessLauncher;
import cbs.nova.starter.services.TemporalDslProcessService;
import cbs.nova.starter.services.TemporalTransactionInvoker;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration
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
  WorkflowClient workflowClient(WorkflowServiceStubs workflowServiceStubs) {
    return WorkflowClient.newInstance(workflowServiceStubs);
  }

  @Bean
  @ConditionalOnMissingBean
  TemporalProcessLauncher temporalProcessLauncher(WorkflowClient workflowClient) {
    return new TemporalDslProcessLauncher(workflowClient, JsonMapper.builder().build());
  }

  @Bean
  @ConditionalOnMissingBean
  TransactionInvoker transactionInvoker() {
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
    return new InMemoryDslRunRepository();
  }

  @Bean
  @ConditionalOnMissingBean
  DevDslRuntime devDslRuntime(
          ExternalCallTracker externalCallTracker,
          ExecutionTraceCollector executionTraceCollector,
          ContextFactory contextFactory) {
    return new DevDslRuntime(externalCallTracker, executionTraceCollector, contextFactory);
  }

  @Bean
  TemporalDslProcessService temporalDslProcessService(ContextFactory contextFactory,
          DslRunRepository runRepository,
          JsonMapper jsonMapper) {
    return new TemporalDslProcessService(contextFactory, runRepository,
            JsonMapper.builder().build());
  }

}
