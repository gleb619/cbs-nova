package cbs.nova.dsl.idea.sync;

import cbs.nova.dsl.idea.DslSyncedDirs;
import com.intellij.openapi.project.Project;

import java.nio.file.Path;
import java.util.Set;

public final class DslSourceRootSyncContributor {

  public void onSyncFinished(Project project, Set<Path> dirs) {
    if (!dirs.isEmpty()) {
      DslSyncedDirs.getInstance(project).replace(dirs);
    }
  }
}
