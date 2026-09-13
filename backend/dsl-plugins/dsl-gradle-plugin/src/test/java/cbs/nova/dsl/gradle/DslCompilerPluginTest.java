package cbs.nova.dsl.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
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
  void dslCompilerConfigurationIsAssignedAsCompileDslTaskClasspath() {
    var project = ProjectBuilder.builder().build();
    project.setVersion("1.2.3");
    project.getPlugins().apply(DslCompilerPlugin.class);

    var task = project.getTasks().named("compileDsl", DslCompileTask.class).get();

    assertThat(task.getClasspath()).isNotNull();
  }

  @Test
  void dslCompilerDefaultDependenciesIncludeStarterByDefault(@TempDir Path projectDir)
          throws Exception {
    Files.writeString(projectDir.resolve("settings.gradle"),
            "rootProject.name = 'dsl-plugin-runtime-defaults'\n");
    Files.writeString(projectDir.resolve("build.gradle"), """
            plugins {
              id 'java'
              id 'cbs.nova.dsl'
            }
            version = '1.2.3'
            """);

    var result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("dependencies", "--configuration", "dslCompiler")
            .withPluginClasspath()
            .build();

    var output = result.getOutput();
    assertThat(output).contains("cbs.nova:starter:1.2.3");
    assertThat(output).contains("cbs.nova:dsl-codegen:1.2.3");
    assertThat(output).contains("cbs.nova:dsl:1.2.3");
    assertThat(output).contains("cbs.nova:dsl-api:1.2.3");
  }

  @Test
  void dslCompilerDefaultDependenciesUseConfiguredRuntimeModule(@TempDir Path projectDir)
          throws Exception {
    Files.writeString(projectDir.resolve("settings.gradle"),
            "rootProject.name = 'dsl-plugin-runtime-custom'\n");
    Files.writeString(projectDir.resolve("build.gradle"), """
            plugins {
              id 'java'
              id 'cbs.nova.dsl'
            }

            dslCompile {
              dslVersion = '1.2.3'
              runtimeModule = 'custom-runtime'
            }
            """);

    var result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("dependencies", "--configuration", "dslCompiler")
            .withPluginClasspath()
            .build();

    var output = result.getOutput();
    assertThat(output).contains("cbs.nova:custom-runtime:1.2.3");
    assertThat(output).doesNotContain("cbs.nova:starter:1.2.3");
  }

  @Test
  void dslCompilerDefaultDependenciesSkipRuntimeModuleWhenBlank(@TempDir Path projectDir)
          throws Exception {
    Files.writeString(projectDir.resolve("settings.gradle"),
            "rootProject.name = 'dsl-plugin-runtime-blank'\n");
    Files.writeString(projectDir.resolve("build.gradle"), """
            plugins {
              id 'java'
              id 'cbs.nova.dsl'
            }

            dslCompile {
              dslVersion = '1.2.3'
              runtimeModule = ''
            }
            """);

    var result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("dependencies", "--configuration", "dslCompiler")
            .withPluginClasspath()
            .build();

    var output = result.getOutput();
    assertThat(output).doesNotContain("cbs.nova:starter");
    assertThat(output).contains("cbs.nova:dsl-codegen");
    assertThat(output).contains("cbs.nova:dsl");
    assertThat(output).contains("cbs.nova:dsl-api");
  }

  @Test
  void mainSourceSetUsesExtensionResourcesDirByDefault() {
    var project = ProjectBuilder.builder().build();
    project.getPlugins().apply("java");
    project.getPlugins().apply(DslCompilerPlugin.class);

    var sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
    var main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);

    assertThat(main.getResources().getSrcDirs())
            .containsExactly(new File(project.getProjectDir(), "src/resources"));
  }

  @Test
  void mainSourceSetUsesConfiguredResourcesDir(@TempDir Path projectDir) throws Exception {
    Files.writeString(projectDir.resolve("settings.gradle"),
            "rootProject.name = 'dsl-plugin-resources-custom'\n");
    Files.writeString(projectDir.resolve("build.gradle"), """
            plugins {
              id 'java'
              id 'cbs.nova.dsl'
            }

            dslCompile {
              resourcesDir = layout.projectDirectory.dir('custom-resources')
            }

            tasks.register('printResourcesDirs') {
              doLast {
                println('RESOURCES=' + sourceSets.main.resources.srcDirs.join(','))
              }
            }
            """);

    var result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("printResourcesDirs")
            .withPluginClasspath()
            .build();

    assertThat(result.getOutput()).contains(
            "RESOURCES=" + projectDir.resolve("custom-resources").toAbsolutePath());
    assertThat(result.getOutput()).doesNotContain("src" + File.separator + "main"
            + File.separator + "resources");
  }

  @Test
  void resourcesFromDefaultDirAreProcessedIntoOutput(@TempDir Path projectDir) throws Exception {
    Files.createDirectories(projectDir.resolve("src/resources/explain"));
    Files.writeString(projectDir.resolve("src/resources/explain/demo.md"), "# demo");
    Files.writeString(projectDir.resolve("settings.gradle"),
            "rootProject.name = 'dsl-plugin-resources-process'\n");
    Files.writeString(projectDir.resolve("build.gradle"), """
            plugins {
              id 'java'
              id 'cbs.nova.dsl'
            }
            version = '0.0.1-SNAPSHOT'
            repositories { mavenLocal(); mavenCentral() }

            dslCompile {
              runtimeModule = ''
            }
            """);

    var result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("processResources")
            .withPluginClasspath()
            .build();

    assertThat(projectDir.resolve("build/resources/main/explain/demo.md")).exists();
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
