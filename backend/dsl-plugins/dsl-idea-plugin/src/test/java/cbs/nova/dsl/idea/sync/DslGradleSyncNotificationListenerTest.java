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
    var taskId = gradleResolveTaskId();
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
