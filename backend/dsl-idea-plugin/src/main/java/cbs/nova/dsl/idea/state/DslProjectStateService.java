package cbs.nova.dsl.idea.state;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Per-project activation gate for the cbs-nova DSL plugin. Set once by
 * {@link DslCheckStartupActivity} using a fast FilenameIndex lookup, then read by all extension
 * points to decide whether DSL features should run.
 */
@Service(Service.Level.PROJECT)
public final class DslProjectStateService {

  private volatile boolean activeDslProject;

  public static @NotNull DslProjectStateService getInstance(@NotNull Project project) {
    var service = project.getService(DslProjectStateService.class);
    if (service == null) {
      throw new IllegalStateException(
              "DslProjectStateService not available for project " + project);
    }
    return service;
  }

  public boolean isActiveDslProject() {
    return activeDslProject;
  }

  public void setActiveDslProject(boolean activeDslProject) {
    this.activeDslProject = activeDslProject;
  }
}
