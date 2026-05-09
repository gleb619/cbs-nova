package cbs.nova.config;

import cbs.dsl.api.SpecDefinitionRegistry;
import cbs.nova.temporal.ActivityManager;
import cbs.nova.temporal.WorkflowManager;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import java.util.Collections;
import java.util.Set;
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
 *   <li>Creates the {@link SpecDefinitionRegistry} bean (produced by the annotation processor).
 *   <li>Creates the {@link ActivityManager} bean.
 *   <li>Creates the {@link WorkflowManager} bean.
 * </ol>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.temporal.enabled", havingValue = "true", matchIfMissing = true)
public class TemporalManagerAutoConfiguration {

  @Value("${app.temporal.service-address:127.0.0.1:7233}")
  private String serviceAddress;

  @Value("${app.temporal.task-queue:}")
  private String defaultTaskQueue;

  /**
   * Creates a {@link WorkflowServiceStubs} bean if the application has not already provided one.
   *
   * @return the service stubs connected to the configured Temporal server address
   */
  @Bean(destroyMethod = "shutdown")
  @ConditionalOnMissingBean
  public WorkflowServiceStubs workflowServiceStubs() {
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
   * Creates the {@link SpecDefinitionRegistry} bean using the compile-time generated
   * implementation.
   *
   * <p>The generated class is produced by {@code dsl-codegen} and knows about every
   * {@code @DslComponent} annotated activity and workflow discovered at compile time.
   *
   * @return the generated specification registry
   */
  @Bean
  @ConditionalOnMissingBean
  public SpecDefinitionRegistry generatedSpecificationRegistry() {
    try {
      Class<?> clazz = Class.forName("cbs.dsl.codegen.generated.GeneratedSpecificationRegistry");
      SpecDefinitionRegistry registry = (SpecDefinitionRegistry) clazz.getDeclaredConstructor().newInstance();
      log.info("Created SpecDefinitionRegistry with {} activities and {} workflows",
          registry.getActivityCodes().size(), registry.getWorkflowCodes().size());
      return registry;
    } catch (Exception e) {
      log.warn("GeneratedSpecificationRegistry not found on classpath — returning empty registry");
      return new SpecDefinitionRegistry() {
        @Override public void registerActivity(String code, Class<?> activityInterface, Object implementation) {}
        @Override public void registerWorkflow(String code, Class<?> workflowInterface, Object implementation) {}
        @Override public Set<String> getActivityCodes() { return Collections.emptySet(); }
        @Override public Set<String> getWorkflowCodes() { return Collections.emptySet(); }
        @Override public Class<?> getActivityInterface(String code) { throw new IllegalArgumentException("No activities registered"); }
        @Override public Class<?> getWorkflowInterface(String code) { throw new IllegalArgumentException("No workflows registered"); }
        @Override public <T> T getActivity(String code, Class<T> activityInterface) { throw new IllegalArgumentException("No activities registered"); }
        @Override public <T> T getWorkflow(String code, Class<T> workflowInterface) { throw new IllegalArgumentException("No workflows registered"); }
      };
    }
  }

  /**
   * Creates the {@link ActivityManager} bean.
   *
   * @param artifactRegistry the generated specification registry
   * @return the activity manager
   */
  @Bean
  public ActivityManager activityManager(SpecDefinitionRegistry artifactRegistry) {
    return new ActivityManager(artifactRegistry);
  }

  /**
   * Creates the {@link WorkflowManager} bean.
   *
   * @param artifactRegistry the generated specification registry
   * @param workflowClient the Temporal workflow client
   * @return the workflow manager
   */
  @Bean
  public WorkflowManager workflowManager(
      SpecDefinitionRegistry artifactRegistry, WorkflowClient workflowClient) {
    return new WorkflowManager(artifactRegistry, workflowClient);
  }
}
