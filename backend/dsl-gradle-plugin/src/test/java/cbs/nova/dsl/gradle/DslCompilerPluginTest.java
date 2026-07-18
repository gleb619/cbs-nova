package cbs.nova.dsl.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import org.gradle.api.artifacts.Configuration;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

class DslCompilerPluginTest {

  @Test
  void applyRegistersDslCompileExtension() {
    var project = ProjectBuilder.builder().build();

    project.getPlugins().apply(DslCompilerPlugin.class);

    assertThat(project.getExtensions().findByName("dslCompile"))
            .isInstanceOf(DslCompileExtension.class);
  }

  @Test
  void applyRegistersCompileDslTask() {
    var project = ProjectBuilder.builder().build();

    project.getPlugins().apply(DslCompilerPlugin.class);

    assertThat(project.getTasks().findByName("compileDsl"))
            .isInstanceOf(DslCompileTask.class);
  }

  @Test
  void compileDslTaskHasBuildGroupAndDescription() {
    var project = ProjectBuilder.builder().build();
    project.getPlugins().apply(DslCompilerPlugin.class);
    var task = project.getTasks().named("compileDsl").get();

    assertThat(task.getGroup()).isEqualTo("build");
    assertThat(task.getDescription()).contains("DSL");
  }

  @Test
  void compileDslTaskSourceAndOutputWiredToExtension() {
    var project = ProjectBuilder.builder().build();
    project.setVersion("1.2.3");
    project.getPlugins().apply(DslCompilerPlugin.class);

    var extension = project.getExtensions().getByType(DslCompileExtension.class);
    var task = project.getTasks().named("compileDsl", DslCompileTask.class).get();

    assertThat(task.getSourceDir().get().getAsFile())
            .isEqualTo(extension.getSourceDir().get().getAsFile());
    assertThat(task.getOutputDir().get().getAsFile())
            .isEqualTo(extension.getOutputDir().get().getAsFile());
    assertThat(task.getDslPackage().get()).isEqualTo(extension.getDslPackage().get());
    assertThat(task.getBuildVersion().get()).isEqualTo(extension.getBuildVersion().get());
    assertThat(task.getLogLevel().get()).isEqualTo(extension.getLogLevel().get());
  }

  @Test
  void applyCreatesDslCompilerConfiguration() {
    var project = ProjectBuilder.builder().build();
    project.getPlugins().apply(DslCompilerPlugin.class);

    assertThat(project.getConfigurations().findByName("dslCompiler"))
            .isInstanceOf(Configuration.class);
  }

  @Test
  void dslCompilerConfigurationFeedsCompileDslTaskClasspath() {
    var project = ProjectBuilder.builder().build();
    project.getPlugins().apply(DslCompilerPlugin.class);

    var config = project.getConfigurations().getByName("dslCompiler");
    var fakeJar = new File(project.getProjectDir(), "fake.jar");
    config.getDependencies().add(project.getDependencies().create(project.files(fakeJar)));

    var task = project.getTasks().named("compileDsl", DslCompileTask.class).get();

    assertThat(task.getClasspath().getFiles()).contains(fakeJar);
  }

  @Test
  void pluginAppliesInRealGradleBuildAndExposesCompileDslTask(@TempDir Path projectDir)
          throws Exception {
    Files.writeString(projectDir.resolve("settings.gradle"),
            "rootProject.name = 'dsl-plugin-smoke'\n");
    Files.writeString(projectDir.resolve("build.gradle"), """
            plugins {
              id 'java'
              id 'cbs.nova.dsl'
            }
            """);

    var result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("tasks", "--group=build")
            .withPluginClasspath()
            .build();

    assertThat(result.getOutput()).contains("compileDsl");
  }
}
