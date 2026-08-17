package cbs.nova.dsl.idea;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Service(Service.Level.PROJECT)
public final class DslSyncedDirs {

  private final Set<Path> dslAndModelDirs = new CopyOnWriteArraySet<>();

  public static DslSyncedDirs getInstance(Project project) {
    return project.getService(DslSyncedDirs.class);
  }

  public void replace(Set<Path> dirs) {
    dslAndModelDirs.clear();
    dslAndModelDirs.addAll(dirs);
  }

  public boolean containsAncestorOf(Path filePath) {
    return dslAndModelDirs.stream().anyMatch(filePath::startsWith);
  }

  public boolean isEmpty() {
    return dslAndModelDirs.isEmpty();
  }
}
