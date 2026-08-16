package cbs.nova.dsl.idea.state;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

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
