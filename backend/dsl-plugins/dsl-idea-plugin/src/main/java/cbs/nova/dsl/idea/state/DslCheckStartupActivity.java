package cbs.nova.dsl.idea.state;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.vfs.VirtualFile;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

public final class DslCheckStartupActivity implements ProjectActivity {

  private static final String DSL_DIR = "src/dsl";
  private static final String MODELS_DIR = "src/models";

  @Override
  public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> completion) {
    if (project.isDisposed()) {
      return Unit.INSTANCE;
    }

    var isDslProject = new AtomicBoolean(false);
    DumbService.getInstance(project).runReadActionInSmartMode(() -> {
      isDslProject.set(hasDslStructure(project));
    });

    DslProjectStateService.getInstance(project).setActiveDslProject(isDslProject.get());
    return Unit.INSTANCE;
  }

  private static boolean hasDslStructure(@NotNull Project project) {
    var projectDir = ProjectUtil.guessProjectDir(project);
    if (projectDir == null || !projectDir.isValid()) {
      return false;
    }

    var dslDir = projectDir.findFileByRelativePath(DSL_DIR);
    var modelsDir = projectDir.findFileByRelativePath(MODELS_DIR);
    return dslDir != null && dslDir.isDirectory()
            && modelsDir != null && modelsDir.isDirectory();
  }
}
