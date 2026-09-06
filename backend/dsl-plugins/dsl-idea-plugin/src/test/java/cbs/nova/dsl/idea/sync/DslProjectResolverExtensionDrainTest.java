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

  private Map<ExternalSystemTaskId, Set<Path>> discoveredRef;
  private Map<ExternalSystemTaskId, Long> firstSeenRef;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    discoveredRef = reflectedMap("DISCOVERED");
    firstSeenRef = reflectedMap("FIRST_SEEN");
    discoveredRef.clear();
    firstSeenRef.clear();
    DslProjectResolverExtension.ttlMillis = DslProjectResolverExtension.DEFAULT_TTL_MILLIS;
    DslProjectResolverExtension.clock = System::currentTimeMillis;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      discoveredRef.clear();
      firstSeenRef.clear();
      DslProjectResolverExtension.ttlMillis = DslProjectResolverExtension.DEFAULT_TTL_MILLIS;
      DslProjectResolverExtension.clock = System::currentTimeMillis;
    } finally {
      super.tearDown();
    }
  }

  @SuppressWarnings("unchecked")
  private static <V> Map<ExternalSystemTaskId, V> reflectedMap(String fieldName) throws Exception {
    var field = DslProjectResolverExtension.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return (Map<ExternalSystemTaskId, V>) field.get(null);
  }

  @SuppressWarnings("unchecked")
  public void testDrainIsIsolatedPerTaskId() throws Exception {
    var taskA = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID,
            ExternalSystemTaskType.RESOLVE_PROJECT, "projectA");
    var taskB = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID,
            ExternalSystemTaskType.RESOLVE_PROJECT, "projectB");

    discoveredRef.computeIfAbsent(taskA, id -> ConcurrentHashMap.newKeySet())
            .add(Path.of("/tmp/projectA/src/dsl"));
    discoveredRef.computeIfAbsent(taskB, id -> ConcurrentHashMap.newKeySet())
            .add(Path.of("/tmp/projectB/src/dsl"));

    var drainedA = DslProjectResolverExtension.drainDiscovered(taskA);
    assertEquals(Set.of(Path.of("/tmp/projectA/src/dsl")), drainedA);

    var drainedB = DslProjectResolverExtension.drainDiscovered(taskB);
    assertEquals(Set.of(Path.of("/tmp/projectB/src/dsl")), drainedB);

    assertTrue(DslProjectResolverExtension.drainDiscovered(taskA).isEmpty());
    assertTrue(DslProjectResolverExtension.drainDiscovered(taskB).isEmpty());
  }

  public void testExpiredEntryPrunedOnDrain() throws Exception {
    var taskA = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID,
            ExternalSystemTaskType.RESOLVE_PROJECT, "projectA");
    var taskB = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID,
            ExternalSystemTaskType.RESOLVE_PROJECT, "projectB");

    long T = 1_000_000L;
    discoveredRef.computeIfAbsent(taskA, id -> ConcurrentHashMap.newKeySet())
            .add(Path.of("/tmp/projectA/src/dsl"));
    firstSeenRef.put(taskA, T);

    DslProjectResolverExtension.ttlMillis = 50L;
    DslProjectResolverExtension.clock = () -> T + 1000L;

    var unrelated = DslProjectResolverExtension.drainDiscovered(taskB);
    assertTrue(unrelated.isEmpty());

    assertFalse(discoveredRef.containsKey(taskA));
    assertFalse(firstSeenRef.containsKey(taskA));
  }

  public void testAbandonedSyncEntryDoesNotLeak() throws Exception {
    var taskA = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID,
            ExternalSystemTaskType.RESOLVE_PROJECT, "projectA");
    var taskB = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID,
            ExternalSystemTaskType.RESOLVE_PROJECT, "projectB");

    long T = 2_000_000L;
    discoveredRef.computeIfAbsent(taskA, id -> ConcurrentHashMap.newKeySet())
            .add(Path.of("/tmp/projectA/src/dsl"));
    firstSeenRef.put(taskA, T);

    DslProjectResolverExtension.ttlMillis = 50L;
    DslProjectResolverExtension.clock = () -> T + 10_000L;

    assertTrue(discoveredRef.containsKey(taskA));
    assertTrue(firstSeenRef.containsKey(taskA));

    var unrelated = DslProjectResolverExtension.drainDiscovered(taskB);
    assertTrue(unrelated.isEmpty());

    assertFalse("abandoned taskA must be evicted after ttl",
            discoveredRef.containsKey(taskA));
    assertFalse("abandoned taskA must be evicted after ttl",
            firstSeenRef.containsKey(taskA));
  }
}
