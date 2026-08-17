package cbs.nova.dsl.codegen.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.event.Level;

import java.nio.file.Path;
import java.util.Properties;

class DslCompilerOptionsTest {

  @TempDir
  Path tempDir;

  @Test
  void parsesAllOptionsFromProperties() {
    var src = tempDir.resolve("src");
    var out = tempDir.resolve("out");
    var props = new Properties();
    props.setProperty("srcDir", src.toString());
    props.setProperty("outputDir", out.toString());
    props.setProperty("buildVersion", "v2");
    props.setProperty("targetPackage", "cbs.nova.dslexamples");
    props.setProperty("logLevel", "DEBUG");
    props.setProperty("classpath", "a.jar:b.jar");
    props.setProperty("useFileNameSubPackage", "false");

    var options = DslCompilerOptions.fromProperties(props);

    assertThat(options.srcDir()).isEqualTo(src);
    assertThat(options.outputDir()).isEqualTo(out);
    assertThat(options.buildVersion()).isEqualTo("v2");
    assertThat(options.targetPackage()).isEqualTo("cbs.nova.dslexamples");
    assertThat(options.logLevel()).isEqualTo(Level.DEBUG);
    assertThat(options.classpath()).isEqualTo("a.jar:b.jar");
    assertThat(options.useFileNameSubPackage()).isFalse();
  }

  @Test
  void appliesDefaultsForMissingKeys() {
    var src = tempDir.resolve("src");
    var out = tempDir.resolve("out");
    var props = new Properties();
    props.setProperty("srcDir", src.toString());
    props.setProperty("outputDir", out.toString());

    var options = DslCompilerOptions.fromProperties(props);

    assertThat(options.buildVersion()).isEqualTo("v1");
    assertThat(options.targetPackage()).isNull();
    assertThat(options.logLevel()).isEqualTo(Level.INFO);
    assertThat(options.classpath()).isNull();
    assertThat(options.useFileNameSubPackage()).isTrue();
  }

  @Test
  void treatsBlankBuildVersionTargetPackageAndClasspathAsNullOrDefault() {
    var src = tempDir.resolve("src");
    var out = tempDir.resolve("out");
    var props = new Properties();
    props.setProperty("srcDir", src.toString());
    props.setProperty("outputDir", out.toString());
    props.setProperty("buildVersion", "   ");
    props.setProperty("targetPackage", "\t");
    props.setProperty("classpath", "");

    var options = DslCompilerOptions.fromProperties(props);

    assertThat(options.buildVersion()).isEqualTo("v1");
    assertThat(options.targetPackage()).isNull();
    assertThat(options.classpath()).isNull();
  }

  @Test
  void parsesUseFileNameSubPackageCaseInsensitively() {
    var src = tempDir.resolve("src");
    var out = tempDir.resolve("out");
    var props = new Properties();
    props.setProperty("srcDir", src.toString());
    props.setProperty("outputDir", out.toString());
    props.setProperty("useFileNameSubPackage", "FALSE");

    assertThat(DslCompilerOptions.fromProperties(props).useFileNameSubPackage()).isFalse();

    props.setProperty("useFileNameSubPackage", "true");
    assertThat(DslCompilerOptions.fromProperties(props).useFileNameSubPackage()).isTrue();
  }

  @Test
  void rejectsMissingSrcDir() {
    var props = new Properties();
    props.setProperty("outputDir", tempDir.resolve("out").toString());

    assertThatThrownBy(() -> DslCompilerOptions.fromProperties(props))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("srcDir");
  }

  @Test
  void rejectsMissingOutputDir() {
    var props = new Properties();
    props.setProperty("srcDir", tempDir.resolve("src").toString());

    assertThatThrownBy(() -> DslCompilerOptions.fromProperties(props))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("outputDir");
  }

  @Test
  void rejectsInvalidLogLevel() {
    var props = new Properties();
    props.setProperty("srcDir", tempDir.resolve("src").toString());
    props.setProperty("outputDir", tempDir.resolve("out").toString());
    props.setProperty("logLevel", "NOPE");

    assertThatThrownBy(() -> DslCompilerOptions.fromProperties(props))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("NOPE");
  }
}
