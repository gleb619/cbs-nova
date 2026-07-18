package cbs.nova.dsl.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import org.gradle.api.tasks.CacheableTask;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.io.File;

class DslCompileTaskTest {

  @Test
  void defaultLogLevelConventionIsTrace() {
    var project = ProjectBuilder.builder().build();
    var task = project.getTasks().register("compileDsl", DslCompileTask.class).get();

    assertThat(task.getLogLevel().get()).isEqualTo("TRACE");
  }

  @Test
  void mainClassIsDslCompiler() {
    var project = ProjectBuilder.builder().build();
    var task = project.getTasks().register("compileDsl", DslCompileTask.class).get();

    assertThat(task.getMainClass().get()).isEqualTo("cbs.nova.dsl.codegen.DslCompiler");
  }

  @Test
  void taskIsAnnotatedCacheableTask() {
    assertThat(DslCompileTask.class.getAnnotation(CacheableTask.class))
            .isNotNull();
  }

  @Test
  void dslPackageAndBuildVersionHaveNoValueBeforeWiring() {
    var project = ProjectBuilder.builder().build();
    var task = project.getTasks().register("compileDsl", DslCompileTask.class).get();

    assertThat(task.getDslPackage().isPresent()).isFalse();
    assertThat(task.getBuildVersion().isPresent()).isFalse();
  }

  @Test
  void sourceAndOutputDirsResolveToExtensionDefaultsWhenPluginApplied() {
    var project = ProjectBuilder.builder().build();
    project.getPlugins().apply(DslCompilerPlugin.class);
    var task = project.getTasks().named("compileDsl", DslCompileTask.class).get();

    assertThat(task.getSourceDir().get().getAsFile())
            .isEqualTo(new File(project.getProjectDir(), "src"));
    assertThat(task.getOutputDir().get().getAsFile())
            .isEqualTo(project.getLayout().getBuildDirectory().dir("generated").get().getAsFile());
  }

  @Test
  void classpathAcceptsArbitraryFileCollection() {
    var project = ProjectBuilder.builder().build();
    var fakeJar = new File(project.getProjectDir(), "fake.jar");
    var config = project.getConfigurations().detachedConfiguration(
            project.getDependencies().create(project.files(fakeJar)));
    var task = project.getTasks()
            .register("compileDsl", DslCompileTask.class, t -> t.setClasspath(config))
            .get();

    assertThat(task.getClasspath().getFiles()).containsExactly(fakeJar);
  }
}
