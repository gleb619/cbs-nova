package cbs.nova.dsl.builder.service;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.exception.CompileException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GradleService {

  private final DslBuilderProperties properties;

  public record BuildOutcome(boolean success, List<String> logLines) {
  }

  public BuildOutcome runBuild(Path projectDir, List<String> tasks) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    try (ProjectConnection connection = GradleConnector.newConnector()
            .forProjectDirectory(projectDir.toFile())
            .connect()) {
      var build = connection.newBuild()
              .forTasks(tasks.toArray(String[]::new))
              .setStandardOutput(stdout)
              .setStandardError(stderr);
      var javaHome = resolveBuildJavaHome();
      if (javaHome != null) {
        log.info("Running Gradle build with Java home {}", javaHome);
        build.setJavaHome(javaHome.toFile());
      }
      build.run();
      return new BuildOutcome(true, logLines(stdout, stderr));
    } catch (BuildException e) {
      return new BuildOutcome(false, logLines(stdout, stderr));
    } catch (GradleConnectionException e) {
      throw new CompileException("Gradle connection failed: " + e.getMessage(),
              logLines(stdout, stderr));
    }
  }

  Path resolveBuildJavaHome() {
    if (properties.gradleJavaHome() != null) {
      return properties.gradleJavaHome();
    }
    var current = Path.of(System.getProperty("java.home"));
    if (isGradleCompatible(current)) {
      return current;
    }
    return installedJdks().stream()
            .filter(this::isGradleCompatible)
            .max(Comparator.comparingInt(this::javaMajor))
            .orElse(current);
  }

  boolean isGradleCompatible(Path javaHome) {
    var major = javaMajor(javaHome);
    return major >= properties.gradleJavaMin() && major <= properties.gradleJavaMax();
  }

  List<Path> installedJdks() {
    var candidates = new ArrayList<Path>();
    var env = System.getenv(properties.jdkHomeEnvVar());
    if (env != null && !env.isBlank()) {
      candidates.add(Path.of(env));
    }
    for (Path root : properties.jdkSearchPaths()) {
      candidates.addAll(listDirs(root));
    }
    return candidates;
  }

  private List<Path> listDirs(Path parent) {
    if (!Files.isDirectory(parent)) {
      return List.of();
    }
    try (var stream = Files.list(parent)) {
      return stream.filter(Files::isDirectory).toList();
    } catch (IOException e) {
      return List.of();
    }
  }

  private int javaMajor(Path javaHome) {
    var release = javaHome.resolve("release");
    if (!Files.exists(release)) {
      return -1;
    }
    try {
      for (var line : Files.readAllLines(release)) {
        if (line.startsWith("JAVA_VERSION=\"")) {
          return parseMajor(line.substring("JAVA_VERSION=\"".length(),
                  line.length() - 1));
        }
      }
    } catch (IOException ignored) {
      return -1;
    }
    return -1;
  }

  private int parseMajor(String version) {
    var normalized = version.startsWith("1.") ? version.substring(2) : version;
    var dot = normalized.indexOf('.');
    var major = dot > 0 ? normalized.substring(0, dot) : normalized;
    try {
      return Integer.parseInt(major);
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  List<String> logLines(ByteArrayOutputStream stdout, ByteArrayOutputStream stderr) {
    var combined = stdout.toString(StandardCharsets.UTF_8)
            + stderr.toString(StandardCharsets.UTF_8);
    var lines = combined.lines().filter(line -> !line.isBlank()).toList();
    var maxLines = properties.buildLogMaxLines();
    return lines.size() <= maxLines
            ? lines
            : lines.subList(lines.size() - maxLines, lines.size());
  }
}
