package cbs.nova.temporal;

import cbs.dsl.api.SpecDefinitionRegistry;
import io.temporal.activity.ActivityOptions;
import io.temporal.worker.Worker;
import io.temporal.workflow.Workflow;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.Set;

/**
 * Deterministic manager for working with generated Temporal activities inside workflows.
 *
 * <p>All state is backed by an immutable {@link SpecDefinitionRegistry} that is injected at
 * construction time. Because the registry contains only pre-configured values, every call is
 * replay-safe and fully deterministic.
 *
 * <p>Typical usage inside a workflow implementation:
 *
 * <pre>
 *   MyActivity stub = activityManager.newActivityStub("DEBIT_FUNDING_ACCOUNT", MyActivity.class);
 *   stub.execute(input);
 * </pre>
 */
@RequiredArgsConstructor
public class ActivityManager {

  private static ActivityManager instance;

  private final SpecDefinitionRegistry artifactRegistry;

  /**
   * Creates a typed activity stub for the given activity code, using default options.
   *
   * @param code the activity code
   * @param activityInterface the activity interface class
   * @param <T> the activity type
   * @return a typed Temporal activity stub
   * @throws IllegalArgumentException if the code is not registered
   */
  public <T> T newActivityStub(String code, Class<T> activityInterface) {
    return newActivityStub(code, activityInterface, defaultOptions());
  }

  /**
   * Creates a typed activity stub for the given activity code with custom options.
   *
   * @param code the activity code
   * @param activityInterface the activity interface class
   * @param options the Temporal activity options
   * @param <T> the activity type
   * @return a typed Temporal activity stub
   * @throws IllegalArgumentException if the code is not registered
   */
  public <T> T newActivityStub(String code, Class<T> activityInterface, ActivityOptions options) {
    // Validate the code exists and the interface matches
    Class<?> registeredInterface = artifactRegistry.getActivityInterface(code);
    if (!registeredInterface.equals(activityInterface)) {
      throw new IllegalArgumentException(
          "Activity '%s' is registered with interface %s but requested %s"
              .formatted(code, registeredInterface.getName(), activityInterface.getName()));
    }
    return Workflow.newActivityStub(activityInterface, options);
  }

  /**
   * Returns the activity interface class registered under the given code.
   *
   * @param code the activity code
   * @return the interface class
   */
  public Class<?> getActivityInterface(String code) {
    return artifactRegistry.getActivityInterface(code);
  }

  /**
   * Returns all registered activity codes.
   *
   * @return unmodifiable set of activity codes
   */
  public Set<String> getActivityCodes() {
    return artifactRegistry.getActivityCodes();
  }

  /**
   * Returns the generated activity implementation for direct invocation (e.g. in preview mode).
   *
   * @param code the activity code
   * @param activityInterface the expected interface class
   * @param <T> the activity type
   * @return the implementation instance
   */
  public <T> T getActivity(String code, Class<T> activityInterface) {
    return artifactRegistry.getActivity(code, activityInterface);
  }

  /**
   * Registers all generated activity implementations from the registry with the given Temporal
   * worker.
   *
   * @param worker the Temporal worker to register activities with
   */
  public void registerActivities(Worker worker) {
    Object[] implementations = artifactRegistry.getActivityCodes().stream()
        .map(
            code -> artifactRegistry.getActivity(code, artifactRegistry.getActivityInterface(code)))
        .toArray();
    if (implementations.length > 0) {
      worker.registerActivitiesImplementations(implementations);
    }
  }

  public static ActivityManager getInstance() {
    if (instance == null) {
      throw new IllegalStateException(
          "ActivityManager not initialized. Call setInstance(ActivityManager) during startup.");
    }
    return instance;
  }

  public static synchronized void setInstance(ActivityManager activityManager) {
    instance = activityManager;
  }

  private static ActivityOptions defaultOptions() {
    return ActivityOptions.newBuilder()
        .setStartToCloseTimeout(Duration.ofSeconds(30))
        .build();
  }
}
