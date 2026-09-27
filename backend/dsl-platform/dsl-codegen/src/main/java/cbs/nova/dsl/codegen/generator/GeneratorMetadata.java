package cbs.nova.dsl.codegen.generator;

import static cbs.nova.dsl.codegen.util.Util.escapeJavaString;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Properties;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GeneratorMetadata {

  private static final String PLATFORM_VERSION_RESOURCE = "cbs-nova-dsl-codegen.properties";
  private static final String UNKNOWN = "unknown";
  private static final String UNEXPANDED_TOKEN = "${dslPlatformVersion}";
  private static final String LOCAL_BUILD = "local";

  private static final String[] BUILD_INFO_ENV_VARS = {
      "DSL_BUILD_INFO", "CI_BUILD_ID", "BUILD_NUMBER", "GITHUB_RUN_ID"
  };

  private static final String[] GIT_INFO_ENV_VARS = {"GIT_COMMIT_SHORT", "GIT_COMMIT"};

  private static final String PLATFORM_VERSION = loadPlatformVersion(
          GeneratorMetadata.class.getClassLoader());
  private static final String DSL_BUILD_INFO = dslBuildInfo();
  private static final String DSL_GIT_INFO = dslGitInfo();

  static @NonNull String annotation(@NonNull Class<?> generatorClass) {
    String generator = generatorClass.getName();
    String timestamp = Instant.now().toString();
    String javaVersion = System.getProperty("java.version", UNKNOWN);
    String user = System.getProperty("user.name", UNKNOWN);
    String comments = "cbs-nova DSL codegen; java.version=%s, user.name=%s, dsl.platform.version=%s"
            .formatted(javaVersion, user, PLATFORM_VERSION);

    return """
            @DslGenerated(
                generator = "%s",
                timestamp = "%s",
                javaVersion = "%s",
                user = "%s",
                dslPlatformVersion = "%s",
                dslBuildInfo = "%s",
                dslGitInfo = "%s")
            @Generated(
                value = "%s",
                date = "%s",
                comments = "%s")
            """.formatted(
            escapeJavaString(generator),
            escapeJavaString(timestamp),
            escapeJavaString(javaVersion),
            escapeJavaString(user),
            escapeJavaString(PLATFORM_VERSION),
            escapeJavaString(DSL_BUILD_INFO),
            escapeJavaString(DSL_GIT_INFO),
            escapeJavaString(generator),
            escapeJavaString(timestamp),
            escapeJavaString(comments));
  }

  static @NonNull String loadPlatformVersion(@NonNull ClassLoader classLoader) {
    try (InputStream in = classLoader.getResourceAsStream(PLATFORM_VERSION_RESOURCE)) {
      if (in == null) {
        return UNKNOWN;
      }
      var properties = new Properties();
      properties.load(in);
      String version = properties.getProperty("dslPlatformVersion");
      if (version == null || version.isBlank() || version.equals(UNEXPANDED_TOKEN)) {
        return UNKNOWN;
      }
      return version;
    } catch (IOException e) {
      return UNKNOWN;
    }
  }

  private static @NonNull String dslBuildInfo() {
    for (String name : BUILD_INFO_ENV_VARS) {
      String value = System.getenv(name);
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return LOCAL_BUILD;
  }

  private static @NonNull String dslGitInfo() {
    for (String name : GIT_INFO_ENV_VARS) {
      String value = System.getenv(name);
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    try {
      String sha = shortGitSha();
      if (!sha.isBlank()) {
        return sha;
      }
    } catch (Exception e) {
      // fall back to unknown
    }
    return UNKNOWN;
  }

  private static @NonNull String shortGitSha() throws IOException, InterruptedException {
    var process = new ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .redirectErrorStream(true)
            .start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
            .trim();
    int exitCode = process.waitFor();
    if (exitCode != 0 || output.isBlank()) {
      throw new IllegalStateException("git rev-parse failed with exit code " + exitCode);
    }
    return output;
  }
}
