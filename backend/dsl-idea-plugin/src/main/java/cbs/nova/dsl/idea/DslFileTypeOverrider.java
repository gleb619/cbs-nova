package cbs.nova.dsl.idea;

import cbs.nova.dsl.idea.state.DslProjectStateService;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public final class DslFileTypeOverrider implements FileTypeOverrider {

  @Override
  public @Nullable FileType getOverriddenFileType(VirtualFile file) {
    if (!"java".equals(file.getExtension())) {
      return null;
    }
    var path = Path.of(file.getPath());
    for (var project : ProjectManager.getInstance().getOpenProjects()) {
      if (!DslProjectStateService.getInstance(project).isActiveDslProject()) {
        continue;
      }
      var synced = DslSyncedDirs.getInstance(project);
      if (!synced.isEmpty() && synced.containsAncestorOf(path)) {
        return CbsDslFileType.INSTANCE;
      }
      if (synced.isEmpty() && matchesFallback(path)) {
        return CbsDslFileType.INSTANCE;
      }
    }
    return null;
  }

  private boolean matchesFallback(Path path) {
    var parts = path.toString().replace('\\', '/');
    return parts.contains("/src/dsl/") || parts.contains("/src/models/");
  }
}
