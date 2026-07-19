package cbs.nova.dsl.idea;

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

/**
 * Tools-menu action that shells out to {@code ./gradlew :<module>:compileDsl} for the selected
 * module. This plugin is a thin IDE layer — all actual DSL compilation is delegated to the existing
 * {@code cbs.nova.dsl} Gradle plugin; this action is its only interactive entry point.
 */
public final class CompileDslAction extends AnAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    if (project == null) {
      return;
    }
    // LangDataKeys.MODULE_CONTEXT/TARGET_MODULE are for module-tree contexts (e.g. Project
    // view); PlatformCoreDataKeys.MODULE (the key LangDataKeys itself resolves via
    // inheritance) is the general "module associated with this action's context" key and is
    // what actually populates from a Tools-menu invocation with an editor/file selection.
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
