package cbs.nova.config;

import cbs.nova.temporal.ActivityStubManager;
import cbs.nova.temporal.WorkflowClientWrapper;
import cbs.nova.temporal.registry.ActivityConfig;
import cbs.nova.temporal.registry.ActivityRegistry;
import cbs.nova.temporal.registry.InMemoryActivityRegistry;
import cbs.nova.temporal.registry.InMemoryWorkflowRegistry;
import cbs.nova.temporal.registry.WorkflowConfig;
import cbs.nova.temporal.registry.WorkflowRegistry;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;

/**
 * Spring Boot auto-configuration for Temporal manager wrappers and registries.
 *
 * <p>This configuration:
 *
 * <ol>
 *   <li>Creates a {@link WorkflowClient} bean (if not already provided by the application).
 *   <li>Creates demo {@link WorkflowRegistry} and {@link ActivityRegistry} beans.
 *   <li>Creates the {@link WorkflowClientWrapper} bean.
 *   <li>Initialises the static {@link ActivityStubManager} before workers start.
 * </ol>
 *
 * <p>The {@link ActivityStubManager} is initialised via a {@link PostConstruct} callback on a
 * dedicated Spring bean. Because Spring context refresh completes before {@code ApplicationRunner}
 * beans (such as {@code TemporalWorkerRegistrar}) execute, the registry is guaranteed to be set
 * before any worker begins polling.
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
   * Demo workflow registry with a single entry. Replace with a DSL-backed implementation in
   * production.
   *
   * @return an in-memory workflow registry
   */
  @Bean
  @ConditionalOnMissingBean
  public WorkflowRegistry workflowRegistry() {
    String taskQueue = defaultTaskQueue.isBlank() ? "cbs-nova-task-queue" : defaultTaskQueue;

    // Demo entry: a generic event workflow placeholder
    Map<String, WorkflowConfig> configs = Map.of(
        "GenericEventWorkflow",
        new WorkflowConfig(
            "GenericEventWorkflow",
            null, // interface class can be resolved later via DSL metadata
            taskQueue,
            Duration.ofMinutes(5),
            Duration.ofMinutes(5),
            "event"));

    log.info("Created InMemoryWorkflowRegistry with {} demo entry(ies)", configs.size());
    return new InMemoryWorkflowRegistry(configs);
  }

  /**
   * Demo activity registry with a single entry. Replace with a DSL-backed implementation in
   * production.
   *
   * @return an in-memory activity registry
   */
  @Bean
  @ConditionalOnMissingBean
  public ActivityRegistry activityRegistry() {
    String taskQueue = defaultTaskQueue.isBlank() ? "cbs-nova-task-queue" : defaultTaskQueue;

    // Demo entry: a generic processing activity placeholder
    Map<String, ActivityConfig> configs = Map.of(
        "ExampleProcessInput",
        new ActivityConfig(
            "ExampleProcessInput",
            null, // interface class can be resolved later via DSL metadata
            taskQueue,
            Duration.ofSeconds(60),
            Duration.ofSeconds(120),
            Duration.ofSeconds(30),
            null));

    log.info("Created InMemoryActivityRegistry with {} demo entry(ies)", configs.size());
    return new InMemoryActivityRegistry(configs);
  }

  /**
   * Creates the {@link WorkflowClientWrapper} bean.
   *
   * @param workflowClient the Temporal workflow client
   * @param workflowRegistry the workflow registry
   * @return the wrapper bean
   */
  @Bean
  public WorkflowClientWrapper workflowClientWrapper(
      WorkflowClient workflowClient, WorkflowRegistry workflowRegistry) {
    return new WorkflowClientWrapper(workflowClient, workflowRegistry);
  }

  /**
   * Bean whose sole purpose is to initialise the static {@link ActivityStubManager} during Spring
   * context refresh.
   *
   * <p>Because this bean is instantiated eagerly, its {@link PostConstruct} method runs before any
   * {@code ApplicationRunner} or {@code CommandLineRunner} beans.
   */
  @Bean
  public ActivityStubManagerInitializer activityStubManagerInitializer(
      ActivityRegistry activityRegistry) {
    return new ActivityStubManagerInitializer(activityRegistry);
  }

  @Slf4j
  @RequiredArgsConstructor
  public static class ActivityStubManagerInitializer {

    private final ActivityRegistry activityRegistry;

    @PostConstruct
    public void init() {
      ActivityStubManager.initialize(activityRegistry);
      log.info(
          "ActivityStubManager initialised with registry: {}",
          activityRegistry.getClass().getSimpleName());
    }
  }
}
