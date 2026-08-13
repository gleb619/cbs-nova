package cbs.nova.dsl.idea.sync;

import cbs.nova.dsl.gradle.tooling.DslProjectModel;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registered against the real extension point declared by the bundled Gradle plugin,
 * {@code org.jetbrains.plugins.gradle.projectResolve} (defaultExtensionNs
 * {@code org.jetbrains.plugins.gradle}) -- NOT {@code com.intellij.gradle.projectResolve} as an
 * earlier sketch of this class assumed. Verified via {@code javap} against
 * {@code org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension} /
 * {@code GradleProjectResolverExtension} bundled with IntelliJ IC 2023.3.5, and by inspecting
 * {@code com.intellij.gradle}'s own {@code META-INF/plugin.xml} inside {@code gradle.jar}, which
 * declares:
 * {@code <extensionPoint qualifiedName="org.jetbrains.plugins.gradle.projectResolve" .../>} and
 * registers its own extensions under
 * {@code <extensions defaultExtensionNs="org.jetbrains.plugins.gradle">}.
 *
 * <p>
 * {@code populateModuleExtraModels(IdeaModule, DataNode<ModuleData>)} and
 * {@code resolverCtx.getExtraProject(IdeaModule, Class)} matched the sketch exactly in this SDK
 * version, so no signature adjustment was needed there.
 *
 * <p>
 * {@link #DISCOVERED} is a static hand-off used to get discovered dirs from this resolver-extension
 * instance (constructed per Gradle sync by the platform, one instance per resolver chain) to
 * {@link DslSourceRootSyncContributor}, which runs later against a {@code Project} once the
 * data-import phase of sync completes. Both run in the same IDE process for the Gradle Tooling API
 * "IN_PROCESS" (default) execution mode used by this plugin's target platform version, so a static
 * field is a safe, simple hand-off. It is keyed per {@link ExternalSystemTaskId} -- the id of the
 * RESOLVE_PROJECT external-system task driving the current Gradle sync, obtained from
 * {@code resolverCtx.getExternalSystemTaskId()} -- rather than being a single shared bucket, so
 * that concurrent syncs of different projects (or, in principle, concurrent module resolution
 * within the same sync) never clobber or cross-deliver each other's discovered directories.
 * {@link ConcurrentHashMap} plus {@link ConcurrentHashMap#newKeySet()} value sets keep both the
 * per-task accumulation and the final drain thread-safe; the drain uses
 * {@link ConcurrentHashMap#remove} to atomically remove-and-return a task's dirs, avoiding the
 * copy-then-clear race a plain {@code Set} would have. This also keeps
 * {@link DslSourceRootSyncContributor} a plain, directly-unit-testable collaborator as required.
 */
public final class DslProjectResolverExtension extends AbstractProjectResolverExtension {

  private static final Map<ExternalSystemTaskId, Set<Path>> DISCOVERED = new ConcurrentHashMap<>();

  @Override
  public Set<Class<?>> getExtraProjectModelClasses() {
    return Set.of(DslProjectModel.class);
  }

  @Override
  public void populateModuleExtraModels(IdeaModule gradleModule,
          DataNode<ModuleData> ideModule) {
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
