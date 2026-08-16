package cbs.nova.dsl.idea;

import cbs.nova.dsl.idea.state.DslProjectStateService;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class CompileDslAction extends AnAction {

  @Override
  public void update(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    boolean active = project != null
            && DslProjectStateService.getInstance(project).isActiveDslProject();
    event.getPresentation().setVisible(active);
    event.getPresentation().setEnabled(active);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    if (project == null
            || project.isDisposed()
            || !DslProjectStateService.getInstance(project).isActiveDslProject()) {
      return;
    }

    Module module = event.getData(PlatformCoreDataKeys.MODULE);
    if (module == null) {
      return;
    }

    var command = gradleCommand(module.getName());
    var commandLine = new GeneralCommandLine(command).withWorkDirectory(project.getBasePath());
    try {
      var processHandler = new OSProcessHandler(commandLine);
      var console = TextConsoleBuilderFactory.getInstance()
              .createBuilder(project).getConsole();
      console.attachToProcess(processHandler);
      RunContentManager.getInstance(project).showRunContent(
              DefaultRunExecutor.getRunExecutorInstance(),
              new RunContentDescriptor(console, processHandler, console.getComponent(),
                      "compileDsl"));
      processHandler.startNotify();
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  List<String> gradleCommand(String moduleName) {
    return List.of("./gradlew", ":%s:compileDsl".formatted(moduleName));
  }
}
