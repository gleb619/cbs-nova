package cbs.nova.dsl.idea.sync;

import cbs.nova.dsl.idea.DslSyncedDirs;
import com.intellij.openapi.project.Project;

/**
 * Plain, directly-unit-testable collaborator: pulls whatever dirs {@link DslProjectResolverExtension}
 * discovered during the just-finished Gradle sync and publishes them into {@link DslSyncedDirs} for
 * the given project. Deliberately has no static Gradle sync listener wiring of its own -- see
 * {@link DslGradleSyncNotificationListener} for the platform hook that calls {@link #onSyncFinished}
 * at the end of a real sync.
 */
public final class DslSourceRootSyncContributor {

  public void onSyncFinished(Project project) {
    var dirs = DslProjectResolverExtension.drainDiscovered();
    if (!dirs.isEmpty()) {
      DslSyncedDirs.getInstance(project).replace(dirs);
    }
  }
}
