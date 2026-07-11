package cbs.nova.dsl.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.io.File;

class DslCompileExtensionTest {

  @Test
  void defaultSourceOutputDirsAndVersion() {
    var project = ProjectBuilder.builder().build();
    project.setVersion("1.2.3");
    var extension = project.getExtensions()
            .create("dslCompile", DslCompileExtension.class, project);

    assertThat(extension.getSourceDir().get().getAsFile())
            .isEqualTo(new File(project.getProjectDir(), "src"));
    assertThat(extension.getOutputDir().get().getAsFile())
            .isEqualTo(new File(project.getBuildDir(), "generated"));
    assertThat(extension.getDslVersion().get()).isEqualTo("1.2.3");
  }
}
