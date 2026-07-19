package cbs.nova.dsl.gradle.tooling;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end test that the {@code cbs.nova.dsl} Gradle plugin actually registers
 * {@link DslModelBuilderService} with Gradle's {@code ToolingModelBuilderRegistry}, so that
 * {@link DslProjectModel} is retrievable through the Tooling API by external clients (e.g. the
 * IntelliJ plugin project-sync). This exercises real plugin discovery/registration, unlike
 * {@link DslModelBuilderServiceTest} which calls the builder directly in isolation.
 */
class DslProjectModelIntegrationTest {

  @TempDir
  Path projectDir;

  private ProjectConnection connection;

  @BeforeEach
  void setUpProject() throws IOException {
    List<String> implementationClasspath = readPluginUnderTestClasspath();
    String classpathDeclaration =
            implementationClasspath.stream()
                    .map(entry -> "files('" + entry.replace("\\", "\\\\") + "')")
                    .collect(Collectors.joining(", "));

    Files.writeString(
            projectDir.resolve("settings.gradle"),
            "rootProject.name = 'dsl-tooling-model-probe'\n");

    Files.writeString(
            projectDir.resolve("build.gradle"),
            "buildscript {\n"
                    + "  dependencies {\n"
                    + "    classpath " + classpathDeclaration + "\n"
                    + "  }\n"
                    + "}\n"
                    + "apply plugin: 'java'\n"
                    + "apply plugin: cbs.nova.dsl.gradle.DslCompilerPlugin\n"
                    + "version = '0.0.1-SNAPSHOT'\n"
                    + "dslCompile {\n"
                    + "  dslPackage = 'cbs.nova.dslprobe'\n"
                    + "}\n"
                    + "repositories { mavenCentral() }\n");

    connection = GradleConnector.newConnector()
            .forProjectDirectory(projectDir.toFile())
            .connect();
  }

  @AfterEach
  void tearDown() {
    if (connection != null) {
      connection.close();
    }
  }

  @Test
  void dslProjectModelIsRetrievableThroughToolingApi() {
    DslProjectModel model = connection.getModel(DslProjectModel.class);

    assertThat(model).isNotNull();
    assertThat(model.getDslSubdir()).isEqualTo("dsl");
    assertThat(model.getModelsSubdir()).isEqualTo("models");
    assertThat(model.getDslPackage()).isEqualTo("cbs.nova.dslprobe");
    assertThat(model.getSourceDir()).isEqualTo(projectDir.resolve("src").toFile());
    assertThat(model.getOutputDir()).isEqualTo(projectDir.resolve("build/generated").toFile());
  }

  /**
   * Reads the {@code implementation-classpath} entries written by the {@code java-gradle-plugin}
   * "pluginUnderTestMetadata" task, so the probe project's buildscript can see the plugin classes
   * (and its dependencies) exactly as the real published plugin would.
   */
  private List<String> readPluginUnderTestClasspath() throws IOException {
    File metadataFile = new File("build/pluginUnderTestMetadata/plugin-under-test-metadata.properties");
    assertThat(metadataFile)
            .as("run the 'pluginUnderTestMetadata' task (a dependency of 'test') before this test")
            .exists();

    Properties properties = new Properties();
    try (var in = Files.newInputStream(metadataFile.toPath())) {
      properties.load(in);
    }
    String classpath = properties.getProperty("implementation-classpath");
    return List.of(classpath.split(File.pathSeparator));
  }
}
