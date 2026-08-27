package cbs.nova.dsl.idea.sync;

import cbs.nova.dsl.gradle.tooling.DslProjectModel;
import cbs.nova.dsl.idea.state.DslProjectStateService;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.project.Project;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DslProjectResolverExtension extends AbstractProjectResolverExtension {

  //TODO: redo to a Caffeine with some properties config for ttl
  private static final Map<ExternalSystemTaskId, Set<Path>> DISCOVERED = new ConcurrentHashMap<>();

  @Override
  public Set<Class<?>> getExtraProjectModelClasses() {
    return Set.of(DslProjectModel.class);
  }

  @Override
  public void populateModuleExtraModels(IdeaModule gradleModule,
          DataNode<ModuleData> ideModule) {
    Project project = resolverCtx.getExternalSystemTaskId().findProject();
    if (project == null
            || project.isDisposed()
            || !DslProjectStateService.getInstance(project).isActiveDslProject()) {
      super.populateModuleExtraModels(gradleModule, ideModule);
      return;
    }

    var model = resolverCtx.getExtraProject(gradleModule, DslProjectModel.class);
    if (model != null) {
      var taskId = resolverCtx.getExternalSystemTaskId();
      var dirs = DISCOVERED.computeIfAbsent(taskId, id -> ConcurrentHashMap.newKeySet());
      dirs.add(model.getSourceDir().toPath().resolve(model.getDslSubdir()));
      dirs.add(model.getSourceDir().toPath().resolve(model.getModelsSubdir()));
    }
    super.populateModuleExtraModels(gradleModule, ideModule);
  }

  public static Set<Path> drainDiscovered(ExternalSystemTaskId taskId) {
    var dirs = DISCOVERED.remove(taskId);
    return dirs == null ? Set.of() : Set.copyOf(dirs);
  }
}
