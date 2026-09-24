package cbs.nova.dsl.builder.service;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class GradleServiceTest {

  @TempDir
  Path tempDir;

  @Test
  void compatWindowComesFromConfig() throws Exception {
    var service = service(properties(11, 17, 200, List.of(jdkRoot()), "DSL_BUILDER_JAVA_HOME"));

    assertThat(service.isGradleCompatible(fakeJdk(jdkRoot(), "jdk-8", "1.8.0_402"))).isFalse();
    assertThat(service.isGradleCompatible(fakeJdk(jdkRoot(), "jdk-17", "17.0.2"))).isTrue();
    assertThat(service.isGradleCompatible(fakeJdk(jdkRoot(), "jdk-21", "21.0.1"))).isFalse();
  }

  @Test
  void resolvesHighestCompatibleJdkFromConfiguredSearchPaths() throws Exception {
    var root = jdkRoot();
    fakeJdk(root, "jdk-11", "11.0.22");
    var jdk17 = fakeJdk(root, "jdk-17", "17.0.2");
    fakeJdk(root, "jdk-21", "21.0.1");
    // max 17 keeps the test JVM (Java 25) out of the window, forcing the search-path fallback
    var service = service(properties(11, 17, 200, List.of(root), "DSL_BUILDER_JAVA_HOME"));

    assertThat(service.resolveBuildJavaHome()).isEqualTo(jdk17);
  }

  @Test
  void logTailCapComesFromConfig() throws Exception {
    var service = service(properties(8, 25, 3, List.of(jdkRoot()), "DSL_BUILDER_JAVA_HOME"));
    var stdout = new ByteArrayOutputStream();
    stdout.writeBytes("one\ntwo\nthree\nfour\nfive\n".getBytes(StandardCharsets.UTF_8));
    var stderr = new ByteArrayOutputStream();
    stderr.writeBytes("six\nseven\n".getBytes(StandardCharsets.UTF_8));

    var lines = service.logLines(stdout, stderr);

    assertThat(lines).containsExactly("five", "six", "seven");
  }

  @Test
  void discoversJdksViaConfiguredSearchPathsOnly() throws Exception {
    var root = jdkRoot();
    var jdk17 = fakeJdk(root, "jdk-17", "17.0.2");
    var plainDir = Files.createDirectories(root.resolve("not-a-jdk"));
    Files.createDirectories(tempDir.resolve("outside-search-paths"));

    var service = service(properties(8, 25, 200, List.of(root, tempDir.resolve("missing")),
            "DSL_BUILDER_JAVA_HOME"));

    var candidates = service.installedJdks();
    assertThat(candidates).contains(jdk17, plainDir);
    assertThat(candidates).noneMatch(path -> path.toString().contains("outside-search-paths"));
  }

  @Test
  void nullPropertiesFallBackToTodayDefaults() {
    var service = service(properties(null, null, null, null, null));

    assertThat(service.isGradleCompatible(Path.of(System.getProperty("java.home")))).isTrue();
    assertThat(service.resolveBuildJavaHome()).isEqualTo(Path.of(System.getProperty("java.home")));
    // default search roots may not exist on this machine — must not throw
    assertThat(service.installedJdks()).isNotNull();
  }

  @Test
  void partialPropertiesFallBackToDefaults() throws Exception {
    var jdk17 = fakeJdk(jdkRoot(), "jdk-17", "17.0.2");
    // only search paths set; min/max/log-lines/env-var fall back to defaults
    var service = service(properties(null, null, null, List.of(jdkRoot()), null));

    assertThat(service.installedJdks()).contains(jdk17);
    assertThat(service.isGradleCompatible(jdk17)).isTrue();
  }

  @Test
  void bindsNewKeysFromConfiguration() {
    var source = new MapConfigurationPropertySource(
            Map.of(
                    "cbs.dsl.builder.gradle-java-min", "8",
                    "cbs.dsl.builder.gradle-java-max", "25",
                    "cbs.dsl.builder.build-log-max-lines", "200",
                    "cbs.dsl.builder.jdk-search-paths[0]", "/opt/jdks",
                    "cbs.dsl.builder.jdk-home-env-var", "CUSTOM_JAVA_HOME"));

    var bound = new Binder(source)
            .bind("cbs.dsl.builder", Bindable.of(DslBuilderProperties.class))
            .get();

    assertThat(bound.gradleJavaMin()).isEqualTo(8);
    assertThat(bound.gradleJavaMax()).isEqualTo(25);
    assertThat(bound.buildLogMaxLines()).isEqualTo(200);
    assertThat(bound.jdkSearchPaths()).containsExactly(Path.of("/opt/jdks"));
    assertThat(bound.jdkHomeEnvVar()).isEqualTo("CUSTOM_JAVA_HOME");
  }

  private GradleService service(DslBuilderProperties properties) {
    return new GradleService(properties);
  }

  private DslBuilderProperties properties(
          Integer min, Integer max, Integer logLines, List<Path> searchPaths, String envVar) {
    return new DslBuilderProperties(
            tempDir,
            Duration.ofMinutes(10),
            Duration.ofHours(1),
            null,
            "0.0.1-SNAPSHOT",
            "1.27.0",
            "4.0.4",
            "v1",
            List.of("clean", "build"),
            List.of("dsl", "models"),
            "project/templates",
            tempDir,
            null,
            null,
            null,
            null,
            new DslBuilderProperties.Bundles(1, 200),
            min,
            max,
            logLines,
            searchPaths,
            envVar,
            null,
            false,
            null);
  }

  private Path jdkRoot() throws IOException {
    return Files.createDirectories(tempDir.resolve("jdks"));
  }

  private Path fakeJdk(Path root, String name, String version) throws IOException {
    var dir = Files.createDirectories(root.resolve(name));
    Files.writeString(dir.resolve("release"), "JAVA_VERSION=\"" + version + "\"\n");
    return dir;
  }
}
