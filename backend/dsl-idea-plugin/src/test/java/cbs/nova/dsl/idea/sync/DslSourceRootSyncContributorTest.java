package cbs.nova.dsl.idea.sync;

import cbs.nova.dsl.idea.DslSyncedDirs;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.nio.file.Path;
import java.util.Set;

public class DslSourceRootSyncContributorTest extends BasePlatformTestCase {

  public void testPushesDiscoveredDirsIntoSyncedDirs() throws Exception {
    var dirs = Set.of(Path.of("/tmp/example/src/dsl"));

    new DslSourceRootSyncContributor().onSyncFinished(getProject(), dirs);

    assertTrue(DslSyncedDirs.getInstance(getProject()).containsAncestorOf(Path.of("/tmp/example/src/dsl/Foo.java")));
  }
}
