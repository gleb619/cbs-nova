package cbs.nova.dsl.idea.sync;

import cbs.nova.dsl.idea.DslSyncedDirs;
import com.intellij.openapi.project.Project;

import java.nio.file.Path;
import java.util.Set;

/**
 * Plain, directly-unit-testable collaborator: publishes whatever dirs
 * {@link DslProjectResolverExtension} discovered during the just-finished Gradle sync into
 * {@link DslSyncedDirs} for the given project. Deliberately has no static Gradle sync listener
 * wiring of its own -- see {@link DslGradleSyncNotificationListener} for the platform hook that
 * calls {@link #onSyncFinished} at the end of a real sync, and that drains the correct
 * per-sync-task set of dirs from {@link DslProjectResolverExtension} before passing them here.
 */
public final class DslSourceRootSyncContributor {

  public void onSyncFinished(Project project, Set<Path> dirs) {
    if (!dirs.isEmpty()) {
      DslSyncedDirs.getInstance(project).replace(dirs);
    }
  }
}
