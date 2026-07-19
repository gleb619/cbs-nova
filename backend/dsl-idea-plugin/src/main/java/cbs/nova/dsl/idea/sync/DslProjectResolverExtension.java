package cbs.nova.dsl.idea.sync;

import cbs.nova.dsl.gradle.tooling.DslProjectModel;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Registered against the real extension point declared by the bundled Gradle plugin,
 * {@code org.jetbrains.plugins.gradle.projectResolve} (defaultExtensionNs
 * {@code org.jetbrains.plugins.gradle}) -- NOT {@code com.intellij.gradle.projectResolve} as an
 * earlier sketch of this class assumed. Verified via {@code javap} against
 * {@code org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension} /
 * {@code GradleProjectResolverExtension} bundled with IntelliJ IC 2023.3.5, and by inspecting
 * {@code com.intellij.gradle}'s own {@code META-INF/plugin.xml} inside {@code gradle.jar}, which
 * declares:
 * {@code <extensionPoint qualifiedName="org.jetbrains.plugins.gradle.projectResolve" .../>}
 * and registers its own extensions under
 * {@code <extensions defaultExtensionNs="org.jetbrains.plugins.gradle">}.
 *
 * <p>{@code populateModuleExtraModels(IdeaModule, DataNode<ModuleData>)} and
 * {@code resolverCtx.getExtraProject(IdeaModule, Class)} matched the sketch exactly in this SDK
 * version, so no signature adjustment was needed there.
 *
 * <p>{@link #DISCOVERED} is a static hand-off used to get discovered dirs from this
 * resolver-extension instance (constructed per Gradle sync by the platform, one instance per
 * resolver chain) to {@link DslSourceRootSyncContributor}, which runs later against a
 * {@code Project} once the data-import phase of sync completes. Both run in the same IDE process
 * for the Gradle Tooling API "IN_PROCESS" (default) execution mode used by this plugin's target
 * platform version, so a static field is a safe, simple hand-off; it also keeps
 * {@link DslSourceRootSyncContributor} a plain, directly-unit-testable collaborator as required.
 */
public final class DslProjectResolverExtension extends AbstractProjectResolverExtension {

  private static final Set<Path> DISCOVERED = new HashSet<>();

  @Override
  public Set<Class<?>> getExtraProjectModelClasses() {
    return Set.of(DslProjectModel.class);
  }

  @Override
  public void populateModuleExtraModels(org.gradle.tooling.model.idea.IdeaModule gradleModule, DataNode<ModuleData> ideModule) {
    var model = resolverCtx.getExtraProject(gradleModule, DslProjectModel.class);
    if (model != null) {
      DISCOVERED.add(model.getSourceDir().toPath().resolve(model.getDslSubdir()));
      DISCOVERED.add(model.getSourceDir().toPath().resolve(model.getModelsSubdir()));
    }
    super.populateModuleExtraModels(gradleModule, ideModule);
  }

  public static Set<Path> drainDiscovered() {
    var copy = Set.copyOf(DISCOVERED);
    DISCOVERED.clear();
    return copy;
  }
}
