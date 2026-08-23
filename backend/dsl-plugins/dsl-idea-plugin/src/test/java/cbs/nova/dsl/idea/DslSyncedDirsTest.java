package cbs.nova.dsl.idea;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DslSyncedDirsTest {

  @Test
  void freshInstanceIsEmpty() {
    var dirs = new DslSyncedDirs();

    assertThat(dirs.isEmpty()).isTrue();
  }

  @Test
  void replaceClearsPriorEntriesAndRepopulatesSet() {
    var dirs = new DslSyncedDirs();
    var initial = Path.of("/tmp/project/src/main/cbs");
    dirs.replace(Set.of(initial));
    assertThat(dirs.isEmpty()).isFalse();

    var next = Path.of("/tmp/project/src/main/another");
    dirs.replace(Set.of(next));

    assertThat(dirs.isEmpty()).isFalse();
    assertThat(dirs.containsAncestorOf(initial)).isFalse();
    assertThat(dirs.containsAncestorOf(next)).isTrue();
  }

  @Test
  void replaceWithEmptySetEmptiesTrackedDirs() {
    var dirs = new DslSyncedDirs();
    dirs.replace(Set.of(Path.of("/tmp/project/src/main/cbs")));

    dirs.replace(Set.of());

    assertThat(dirs.isEmpty()).isTrue();
  }

  @Test
  void containsAncestorOfReturnsTrueForTrackedDirItself() {
    var dirs = new DslSyncedDirs();
    var tracked = Path.of("/tmp/project/src/main/cbs");
    dirs.replace(Set.of(tracked));

    assertThat(dirs.containsAncestorOf(tracked)).isTrue();
  }

  @Test
  void containsAncestorOfReturnsTrueForDescendantOfTrackedDir() {
    var dirs = new DslSyncedDirs();
    var tracked = Path.of("/tmp/project/src/main/cbs");
    dirs.replace(Set.of(tracked));

    var descendant = Path.of("/tmp/project/src/main/cbs/sub/Foo.cbs");
    assertThat(dirs.containsAncestorOf(descendant)).isTrue();
  }

  @Test
  void containsAncestorOfReturnsFalseForUnrelatedSiblingPath() {
    var dirs = new DslSyncedDirs();
    dirs.replace(Set.of(Path.of("/tmp/project/src/main/cbs")));

    var sibling = Path.of("/tmp/project/src/test/java/Foo.java");
    assertThat(dirs.containsAncestorOf(sibling)).isFalse();
  }
}
