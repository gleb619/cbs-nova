package cbs.nova.config;

import cbs.dsl.api.DslComponentResolver;
import cbs.dsl.api.SpecDefinitionRegistry;
import cbs.nova.registry.DefaultSpecDefinitionRegistry;
import cbs.nova.registry.SpiSpecDefinitionRegistryLoader;
import cbs.nova.temporal.ActivityManager;
import cbs.nova.temporal.WorkflowManager;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-configuration for Temporal managers and the generated specification registry.
 *
 * <p>This configuration:
 *
 * <ol>
 *   <li>Creates a {@link WorkflowClient} bean (if not already provided by the application).
 *   <li>Creates the {@link SpecDefinitionRegistry} bean populated via SPI.
 *   <li>Creates the {@link ActivityManager} bean.
 *   <li>Creates the {@link WorkflowManager} bean.
 * </ol>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.temporal.enabled", havingValue = "true", matchIfMissing = true)
public class TemporalManagerAutoConfiguration {

  /**
   * Creates a {@link WorkflowServiceStubs} bean if the application has not already provided one.
   *
   * @return the service stubs connected to the configured Temporal server address
   */
  @Bean(destroyMethod = "shutdown")
  @ConditionalOnMissingBean
  public WorkflowServiceStubs workflowServiceStubs(
      @Value("${app.temporal.service-address:127.0.0.1:7233}") String serviceAddress) {
    return WorkflowServiceStubs.newServiceStubs(
        WorkflowServiceStubsOptions.newBuilder().setTarget(serviceAddress).build());
  }

  /**
   * Creates a {@link WorkflowClient} bean if the application has not already provided one.
   *
   * @param stubs the service stubs
   * @return a new workflow client instance
   */
  @Bean
  @ConditionalOnMissingBean
  public WorkflowClient workflowClient(WorkflowServiceStubs stubs) {
    return WorkflowClient.newInstance(stubs);
  }

  /**
   * Creates the {@link DefaultSpecDefinitionRegistry} bean and populates it by loading all
   * {@link cbs.dsl.api.SpecDefinitionRegistryProvider} implementations via SPI.
   *
   * @param resolver the component resolver for Spring-managed beans
   * @return the populated specification registry
   */
  @Bean
  public DefaultSpecDefinitionRegistry generatedSpecDefinitionRegistry(
      DslComponentResolver resolver) {
    DefaultSpecDefinitionRegistry registry = new DefaultSpecDefinitionRegistry();
    SpiSpecDefinitionRegistryLoader.loadInto(registry, resolver);
    log.info(
        "Created SpecDefinitionRegistry with {} activities and {} workflows",
        registry.getActivityCodes().size(),
        registry.getWorkflowCodes().size());
    return registry;
  }

  /**
   * Exposes the generated {@link DefaultSpecDefinitionRegistry} as a {@link SpecDefinitionRegistry}
   * bean.
   *
   * @param generatedSpecDefinitionRegistry the concrete registry instance
   * @return the specification registry
   */
  @Bean
  @ConditionalOnMissingBean
  public SpecDefinitionRegistry specDefinitionRegistry(
      DefaultSpecDefinitionRegistry generatedSpecDefinitionRegistry) {
    return generatedSpecDefinitionRegistry;
  }

  /**
   * Creates the {@link ActivityManager} bean.
   *
   * @param specDefinitionRegistry the generated specification registry
   * @return the activity manager
   */
  @Bean
  public ActivityManager activityManager(SpecDefinitionRegistry specDefinitionRegistry) {
    ActivityManager manager = new ActivityManager(specDefinitionRegistry);
    ActivityManager.setInstance(manager);
    log.info("ActivityManager static instance set");
    return manager;
  }

  /**
   * Creates the {@link WorkflowManager} bean.
   *
   * @param specDefinitionRegistry the generated specification registry
   * @param workflowClient the Temporal workflow client
   * @return the workflow manager
   */
  @Bean
  public WorkflowManager workflowManager(
      SpecDefinitionRegistry specDefinitionRegistry, WorkflowClient workflowClient) {
    return new WorkflowManager(specDefinitionRegistry, workflowClient);
  }
}
