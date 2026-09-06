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
import java.util.function.LongSupplier;

public final class DslProjectResolverExtension extends AbstractProjectResolverExtension {

  static final long DEFAULT_TTL_MILLIS = 600_000L;
  static volatile long ttlMillis = DEFAULT_TTL_MILLIS;
  static LongSupplier clock = System::currentTimeMillis;

  private static final Map<ExternalSystemTaskId, Set<Path>> DISCOVERED = new ConcurrentHashMap<>();
  private static final Map<ExternalSystemTaskId, Long> FIRST_SEEN = new ConcurrentHashMap<>();

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
      FIRST_SEEN.putIfAbsent(taskId, clock.getAsLong());
      dirs.add(model.getSourceDir().toPath().resolve(model.getDslSubdir()));
      dirs.add(model.getSourceDir().toPath().resolve(model.getModelsSubdir()));
    }
    super.populateModuleExtraModels(gradleModule, ideModule);
    pruneExpired();
  }

  public static Set<Path> drainDiscovered(ExternalSystemTaskId taskId) {
    pruneExpired();
    FIRST_SEEN.remove(taskId);
    var dirs = DISCOVERED.remove(taskId);
    return dirs == null ? Set.of() : Set.copyOf(dirs);
  }

  private static void pruneExpired() {
    long now = clock.getAsLong();
    long ttl = ttlMillis;
    FIRST_SEEN.forEach((id, firstSeen) -> {
      if (now - firstSeen > ttl) {
        FIRST_SEEN.remove(id);
        DISCOVERED.remove(id);
      }
    });
  }
}
