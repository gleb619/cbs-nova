package cbs.nova.dsl.idea.sync;

import cbs.nova.dsl.idea.state.DslProjectStateService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public final class DslGradleSyncNotificationListener
        implements
          ExternalSystemTaskNotificationListener {

  private static final Logger LOG = Logger.getInstance(DslGradleSyncNotificationListener.class);

  private final DslSourceRootSyncContributor contributor = new DslSourceRootSyncContributor();

  @Override
  public void onStart(ExternalSystemTaskId id) {
    log("onStart, id=" + id);
  }

  @Override
  public void onStatusChange(ExternalSystemTaskNotificationEvent event) {
    log("onStatusChange, event=" + event);
  }

  @Override
  public void onTaskOutput(ExternalSystemTaskId id, String text, boolean stdOut) {
    log("onTaskOutput, id=" + id);
  }

  @Override
  public void onEnd(ExternalSystemTaskId id) {
    log("onEnd, id=" + id);
  }

  @Override
  public void onSuccess(ExternalSystemTaskId id) {
    log("onSuccess, id=" + id);
    if (!isGradleProjectResolve(id)) {
      return;
    }
    var project = id.findProject();
    if (project == null
            || project.isDisposed()
            || !DslProjectStateService.getInstance(project).isActiveDslProject()) {
      return;
    }
    var dirs = DslProjectResolverExtension.drainDiscovered(id);
    contributor.onSyncFinished(project, dirs);
  }

  @Override
  public void onFailure(ExternalSystemTaskId id, Exception e) {
    log("onFailure, id=" + id);
  }

  @Override
  public void beforeCancel(ExternalSystemTaskId id) {
    log("beforeCancel, id=" + id);
  }

  @Override
  public void onCancel(ExternalSystemTaskId id) {
    log("onCancel, id=" + id);
  }

  private boolean isGradleProjectResolve(ExternalSystemTaskId id) {
    ProjectSystemId systemId = id.getProjectSystemId();
    return id.getType() == ExternalSystemTaskType.RESOLVE_PROJECT
            && GradleConstants.SYSTEM_ID.equals(systemId);
  }

  private void log(String message) {
    LOG.debug("DslGradleSyncNotificationListener: " + message);
  }
}
