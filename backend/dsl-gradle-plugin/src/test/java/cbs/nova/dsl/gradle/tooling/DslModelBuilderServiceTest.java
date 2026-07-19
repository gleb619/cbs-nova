package cbs.nova.dsl.gradle.tooling;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.gradle.DslCompileExtension;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.io.File;

class DslModelBuilderServiceTest {

  @Test
  void canBuildOnlyForDslProjectModel() {
    var service = new DslModelBuilderService();
    assertThat(service.canBuild(DslProjectModel.class.getName())).isTrue();
    assertThat(service.canBuild("some.other.Model")).isFalse();
  }

  @Test
  void buildAllReadsExtensionValues() {
    Project project = ProjectBuilder.builder().build();
    project.getPlugins().apply("java");
    var extension = project.getExtensions().create("dslCompile", DslCompileExtension.class,
            project);
    extension.getSourceDir().set(new File(project.getProjectDir(), "src"));
    extension.getOutputDir().set(new File(project.getProjectDir(), "build/generated"));
    extension.getDslPackage().set("cbs.nova.dslexamples");

    var model = new DslModelBuilderService().buildAll(DslProjectModel.class.getName(), project);

    assertThat(model.getSourceDir()).isEqualTo(new File(project.getProjectDir(), "src"));
    assertThat(model.getDslSubdir()).isEqualTo("dsl");
    assertThat(model.getModelsSubdir()).isEqualTo("models");
    assertThat(model.getOutputDir())
            .isEqualTo(new File(project.getProjectDir(), "build/generated"));
    assertThat(model.getDslPackage()).isEqualTo("cbs.nova.dslexamples");
  }
}
