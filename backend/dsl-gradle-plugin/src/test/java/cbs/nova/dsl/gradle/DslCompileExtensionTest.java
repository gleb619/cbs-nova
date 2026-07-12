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
            .isEqualTo(project.getLayout().getBuildDirectory().dir("generated").get().getAsFile());
    assertThat(extension.getDslVersion().get()).isEqualTo("1.2.3");
    assertThat(extension.getDslPackage().get()).isEmpty();
    assertThat(extension.getBuildVersion().get()).isEmpty();
    assertThat(extension.getLogLevel().get()).isEqualTo("TRACE");
  }
}
