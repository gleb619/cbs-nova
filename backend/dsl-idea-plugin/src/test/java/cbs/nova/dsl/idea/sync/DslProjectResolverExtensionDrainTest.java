package cbs.nova.dsl.idea.sync;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DslProjectResolverExtensionDrainTest extends BasePlatformTestCase {

  @SuppressWarnings("unchecked")
  public void testDrainIsIsolatedPerTaskId() throws Exception {
    var field = DslProjectResolverExtension.class.getDeclaredField("DISCOVERED");
    field.setAccessible(true);
    var discovered = (Map<ExternalSystemTaskId, Set<Path>>) field.get(null);

    var taskA = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID,
            ExternalSystemTaskType.RESOLVE_PROJECT, "projectA");
    var taskB = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID,
            ExternalSystemTaskType.RESOLVE_PROJECT, "projectB");

    discovered.computeIfAbsent(taskA, id -> ConcurrentHashMap.newKeySet())
            .add(Path.of("/tmp/projectA/src/dsl"));
    discovered.computeIfAbsent(taskB, id -> ConcurrentHashMap.newKeySet())
            .add(Path.of("/tmp/projectB/src/dsl"));

    var drainedA = DslProjectResolverExtension.drainDiscovered(taskA);
    assertEquals(Set.of(Path.of("/tmp/projectA/src/dsl")), drainedA);

    var drainedB = DslProjectResolverExtension.drainDiscovered(taskB);
    assertEquals(Set.of(Path.of("/tmp/projectB/src/dsl")), drainedB);

    assertTrue(DslProjectResolverExtension.drainDiscovered(taskA).isEmpty());
    assertTrue(DslProjectResolverExtension.drainDiscovered(taskB).isEmpty());
  }
}
