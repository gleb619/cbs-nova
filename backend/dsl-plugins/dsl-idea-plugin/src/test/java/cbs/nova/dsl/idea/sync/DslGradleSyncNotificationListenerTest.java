package cbs.nova.dsl.idea.sync;

import cbs.nova.dsl.idea.DslSyncedDirs;
import cbs.nova.dsl.idea.state.DslProjectStateService;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DslGradleSyncNotificationListenerTest extends BasePlatformTestCase {

  @SuppressWarnings("unchecked")
  private static Map<ExternalSystemTaskId, Set<Path>> discoveredMap() throws Exception {
    var field = DslProjectResolverExtension.class.getDeclaredField("DISCOVERED");
    field.setAccessible(true);
    return (Map<ExternalSystemTaskId, Set<Path>>) field.get(null);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    // DslSyncedDirs is a project-level service backed by the SAME in-memory project
    // instance for the whole test class. Reset it so no run pushes leak into
    // a later test's isEmpty() assertion regardless of method execution order.
    DslSyncedDirs.getInstance(getProject()).replace(Set.of());
  }

  public void testOnSuccessIgnoresNonGradleSystemId() throws Exception {
    var taskId = ExternalSystemTaskId.create(
            new ProjectSystemId("NOT_GRADLE"),
            ExternalSystemTaskType.RESOLVE_PROJECT,
            getProject().getName());
    discoveredMap().computeIfAbsent(taskId, id -> ConcurrentHashMap.newKeySet())
            .add(Path.of("/tmp/should-not-be-pushed"));

    new DslGradleSyncNotificationListener().onSuccess(taskId);

    assertTrue(DslSyncedDirs.getInstance(getProject()).isEmpty());
  }

  public void testOnSuccessIgnoresNonResolveTaskType() throws Exception {
    var taskId = ExternalSystemTaskId.create(
            GradleConstants.SYSTEM_ID,
            ExternalSystemTaskType.EXECUTE_TASK,
            getProject().getName());
    discoveredMap().computeIfAbsent(taskId, id -> ConcurrentHashMap.newKeySet())
            .add(Path.of("/tmp/should-not-be-pushed"));

    new DslGradleSyncNotificationListener().onSuccess(taskId);

    assertTrue(DslSyncedDirs.getInstance(getProject()).isEmpty());
  }

  public void testOnSuccessIsNoOpWhenProjectIsNotActiveDsl() throws Exception {
    DslProjectStateService.getInstance(getProject()).setActiveDslProject(false);
    var taskId = gradleResolveTaskId();
    discoveredMap().computeIfAbsent(taskId, id -> ConcurrentHashMap.newKeySet())
            .add(Path.of("/tmp/should-not-be-pushed"));

    new DslGradleSyncNotificationListener().onSuccess(taskId);

    assertTrue(DslSyncedDirs.getInstance(getProject()).isEmpty());
  }

  public void testOnSuccessDoesNotPushDirsWhenFindProjectReturnsNull() throws Exception {
    DslProjectStateService.getInstance(getProject()).setActiveDslProject(true);
    // Use a task id whose project is NOT resolvable (no matching open project), so
    // ExternalSystemTaskId.findProject() deterministically returns null. Using the
    // fixture's real basePath makes findProject() resolve and the listener PUSHES.
    var taskId = ExternalSystemTaskId.create(
            GradleConstants.SYSTEM_ID,
            ExternalSystemTaskType.RESOLVE_PROJECT,
            "does-not-exist-gradle-project");
    var dslDir = Path.of("/tmp/test-push/src/dsl");
    discoveredMap().computeIfAbsent(taskId, id -> ConcurrentHashMap.newKeySet()).add(dslDir);

    new DslGradleSyncNotificationListener().onSuccess(taskId);

    assertTrue(DslSyncedDirs.getInstance(getProject()).isEmpty());
  }

  public void testNonSuccessCallbacksAreSafeAndHaveNoSideEffects() {
    var listener = new DslGradleSyncNotificationListener();
    var taskId = ExternalSystemTaskId.create(
            GradleConstants.SYSTEM_ID,
            ExternalSystemTaskType.RESOLVE_PROJECT,
            getProject().getName());

    listener.onStart(taskId);
    listener.onStatusChange(null);
    listener.onTaskOutput(taskId, "anything", true);
    listener.onEnd(taskId);
    listener.onFailure(taskId, new RuntimeException("boom"));
    listener.beforeCancel(taskId);
    listener.onCancel(taskId);

    assertTrue(DslSyncedDirs.getInstance(getProject()).isEmpty());
  }

  private ExternalSystemTaskId gradleResolveTaskId() {
    // Use the test project's base path so findProject() resolves back to our fixture.
    var basePath = getProject().getBasePath();
    return ExternalSystemTaskId.create(
            GradleConstants.SYSTEM_ID,
            ExternalSystemTaskType.RESOLVE_PROJECT,
            basePath != null ? basePath : getProject().getName());
  }

}
