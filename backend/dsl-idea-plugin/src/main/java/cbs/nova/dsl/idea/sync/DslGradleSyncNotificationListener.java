package cbs.nova.dsl.idea.sync;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * Registered as a top-level {@code com.intellij.externalSystemTaskNotificationListener} extension
 * (same extension point the bundled Gradle plugin itself uses, e.g. for
 * {@code GradleInstallationManager$BuildLayoutParametersCacheCleanupListener} in
 * {@code com.intellij.gradle}'s own plugin.xml). {@code onSuccess} fires once the whole
 * RESOLVE_PROJECT external-system task -- including data import into the IDE project model --
 * completes for a Gradle sync, which is the "end of sync" hook this plugin needs.
 *
 * <p>{@link ExternalSystemTaskId#findProject()} resolves the {@code Project} instance the task
 * ran against, letting this listener stay a thin adapter that just forwards to the plain,
 * directly-testable {@link DslSourceRootSyncContributor}.
 */
public final class DslGradleSyncNotificationListener implements ExternalSystemTaskNotificationListener {

  private final DslSourceRootSyncContributor contributor = new DslSourceRootSyncContributor();

  @Override
  public void onStart(ExternalSystemTaskId id) {
  }

  @Override
  public void onStatusChange(ExternalSystemTaskNotificationEvent event) {
  }

  @Override
  public void onTaskOutput(ExternalSystemTaskId id, String text, boolean stdOut) {
  }

  @Override
  public void onEnd(ExternalSystemTaskId id) {
  }

  @Override
  public void onSuccess(ExternalSystemTaskId id) {
    if (!isGradleProjectResolve(id)) {
      return;
    }
    var project = id.findProject();
    if (project != null && !project.isDisposed()) {
      contributor.onSyncFinished(project);
    }
  }

  @Override
  public void onFailure(ExternalSystemTaskId id, Exception e) {
  }

  @Override
  public void beforeCancel(ExternalSystemTaskId id) {
  }

  @Override
  public void onCancel(ExternalSystemTaskId id) {
  }

  private boolean isGradleProjectResolve(ExternalSystemTaskId id) {
    ProjectSystemId systemId = id.getProjectSystemId();
    return id.getType() == ExternalSystemTaskType.RESOLVE_PROJECT
        && GradleConstants.SYSTEM_ID.equals(systemId);
  }
}
