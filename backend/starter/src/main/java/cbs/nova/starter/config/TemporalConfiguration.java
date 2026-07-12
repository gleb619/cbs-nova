package cbs.nova.starter.config;

import cbs.nova.dsl.DslRuntime;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.TemporalProcessLauncher;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.DevDslRuntime;
import cbs.nova.starter.ExternalCallTracker;
import cbs.nova.starter.controllers.DslIntrospectionResource;
import cbs.nova.starter.controllers.DslRuntimeResource;
import cbs.nova.starter.services.TemporalDslProcessLauncher;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

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
    return new TemporalDslProcessLauncher(workflowClient);
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
  DevDslRuntime devDslRuntime(
          ExternalCallTracker externalCallTracker,
          ExecutionTraceCollector executionTraceCollector,
          ContextFactory contextFactory) {
    return new DevDslRuntime(externalCallTracker, executionTraceCollector, contextFactory);
  }

  @Bean
  @ConditionalOnMissingBean
  DslRuntimeResource dslRuntimeResource(DslRuntime dslRuntime, ContextFactory contextFactory) {
    return new DslRuntimeResource(dslRuntime, contextFactory);
  }

  @Bean
  @ConditionalOnMissingBean
  DslIntrospectionResource dslIntrospectionResource() {
    return new DslIntrospectionResource();
  }
}
