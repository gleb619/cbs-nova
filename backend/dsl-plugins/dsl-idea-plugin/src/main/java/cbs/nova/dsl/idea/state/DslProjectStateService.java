package cbs.nova.dsl.idea.state;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
public final class DslProjectStateService {

  private final AtomicBoolean activeDslProject = new AtomicBoolean();

  public static @NotNull DslProjectStateService getInstance(@NotNull Project project) {
    var service = project.getService(DslProjectStateService.class);
    if (service == null) {
      throw new IllegalStateException(
              "DslProjectStateService not available for project " + project);
    }
    return service;
  }

  public boolean isActiveDslProject() {
    return activeDslProject.get();
  }

  public void setActiveDslProject(boolean activeDslProject) {
    this.activeDslProject.set(activeDslProject);
  }
}
