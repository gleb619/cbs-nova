package cbs.nova.dsl.idea.sync;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;

import java.nio.file.Path;
import java.util.Set;
import java.util.function.Predicate;

public final class DslSourceRootExcluder {

  private static final String DSL_SEGMENT = "/src/dsl";
  private static final String MODELS_SEGMENT = "/src/models";

  public void excludeDiscovered(Project project, Set<Path> dirs) {
    if (dirs.isEmpty()) {
      return;
    }
    excludeUnder(project, path -> dirs.stream().anyMatch(path::startsWith));
  }

  public void excludeCompactSources(Project project) {
    excludeUnder(project, DslSourceRootExcluder::underCompactSourceDir);
  }

  private void excludeUnder(Project project, Predicate<Path> matches) {
    if (project.isDisposed()) {
      return;
    }
    for (var module : ModuleManager.getInstance(project).getModules()) {
      var model = ModuleRootManager.getInstance(module).getModifiableModel();
      var changed = false;
      try {
        for (var entry : model.getContentEntries()) {
          for (var folder : entry.getSourceFolders()) {
            var file = folder.getFile();
            if (file == null || !matches.test(Path.of(file.getPath()))) {
              continue;
            }
            entry.removeSourceFolder(folder);
            changed = true;
          }
        }
      } finally {
        if (!changed) {
          model.dispose();
        }
      }
      if (changed) {
        WriteAction.run(model::commit);
      }
    }
  }

  private static boolean underCompactSourceDir(Path path) {
    var normalized = path.toString().replace('\\', '/');
    return normalized.contains(DSL_SEGMENT + "/") || normalized.endsWith(DSL_SEGMENT)
            || normalized.contains(MODELS_SEGMENT + "/") || normalized.endsWith(MODELS_SEGMENT);
  }
}
