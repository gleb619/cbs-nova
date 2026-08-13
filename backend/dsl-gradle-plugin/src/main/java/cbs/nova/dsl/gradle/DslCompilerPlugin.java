package cbs.nova.dsl.gradle;

import cbs.nova.dsl.gradle.tooling.DslModelBuilderService;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import javax.inject.Inject;

import java.util.Map;

public class DslCompilerPlugin implements Plugin<Project> {

  private final ToolingModelBuilderRegistry registry;

  @Inject
  public DslCompilerPlugin(ToolingModelBuilderRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void apply(Project project) {
    registry.register(new DslModelBuilderService());

    var extension = project.getExtensions()
            .create("dslCompile", DslCompileExtension.class, project);

    var dslCompiler = project.getConfigurations().create("dslCompiler");

    // dslCompiler.exclude(Map.of("group", "ch.qos.logback"));
    // dslCompiler.exclude(Map.of(
    // "group", "org.springframework.boot",
    // "module", "spring-boot-starter-logging"));

    dslCompiler.getDependencies().addLater(
            extension.getDslVersion()
                    .map(v -> project.getDependencies().create("cbs.nova:dsl-codegen:" + v)));
    dslCompiler.getDependencies().addLater(
            extension.getDslVersion()
                    .map(v -> project.getDependencies().create("cbs.nova:dsl:" + v)));
    dslCompiler.getDependencies().addLater(
            extension.getDslVersion()
                    .map(v -> project.getDependencies().create("cbs.nova:dsl-api:" + v)));
    dslCompiler.getDependencies().addLater(extension
            .getRuntimeModule().zip(extension.getDslVersion(),
                    (runtimeModule, v) -> runtimeModule.isBlank()
                            ? null
                            : project.getDependencies()
                                    .create("cbs.nova:" + runtimeModule + ":" + v))
            .filter(java.util.Objects::nonNull));

    var compileDsl = project.getTasks().register("compileDsl", DslCompileTask.class, task -> {
      task.setGroup("build");
      task.setDescription("Compiles DSL compact source files with DslCompiler");
      task.getSourceDir().set(extension.getSourceDir());
      task.getOutputDir().set(extension.getOutputDir());
      task.getDslPackage().set(extension.getDslPackage());
      task.getBuildVersion().set(extension.getBuildVersion());
      task.getLogLevel().set(extension.getLogLevel());
      task.getRuntimeModule().convention(extension.getRuntimeModule());
      task.setClasspath(dslCompiler);
    });

    project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
      var sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
      var main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);

      main.getJava().srcDir(extension.getOutputDir());
      main.getJava().exclude("**/*.class");
      main.setCompileClasspath(
              main.getCompileClasspath().plus(project.files(extension.getOutputDir())));

      project.getTasks().named("processResources", ProcessResources.class, processResources -> {
        processResources.from(extension.getOutputDir(), spec -> spec.include("META-INF/**"));
        processResources.dependsOn(compileDsl);
      });

      project.getTasks().named("compileJava", JavaCompile.class, compileJava -> {
        compileJava.dependsOn(compileDsl);
      });

      project.getTasks().register("copyGeneratedClasses", Copy.class, copy -> {
        copy.from(extension.getOutputDir());
        copy.include("**/*.class");
        copy.into(project.getLayout().getBuildDirectory().dir("classes/java/main"));
        copy.mustRunAfter("compileJava");
      });

      project.getTasks().named("jar", jar -> {
        jar.dependsOn("copyGeneratedClasses");
      });
    });
  }
}
