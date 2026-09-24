package cbs.nova.dsl.builder.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("cbs.dsl.builder")
public record DslBuilderProperties(
        Path workspaceDir,
        Duration cleanupInterval,
        Duration sessionTtl,
        Path gradleJavaHome,
        String dslVersion,
        String temporalVersion,
        String springBootVersion,
        String defaultBuildVersion,
        List<String> buildTasks,
        List<String> sourceFolders,
        String templatesDir,
        Path sourceDir,
        @DefaultValue Queue queue,
        @DefaultValue Files files,
        @DefaultValue Git git,
        @DefaultValue FileBuffer fileBuffer,
        @DefaultValue Bundles bundles,
        Integer gradleJavaMin,
        Integer gradleJavaMax,
        Integer buildLogMaxLines,
        List<Path> jdkSearchPaths,
        String jdkHomeEnvVar,
        List<String> allowedRepoSchemes,
        @DefaultValue("false") boolean allowPlainHttpRepo,
        List<String> allowedRepoHosts) {

  public DslBuilderProperties {
    queue = queue == null ? new Queue(100, 4) : queue;
    files = files == null ? new Files(5, 100, 32, 8, 5L) : files;
    git = git == null ? new Git(true, null, null, null, null, null, 5, false, "origin") : git;
    fileBuffer = fileBuffer == null ? new FileBuffer(1000, 3600L) : fileBuffer;
    bundles = bundles == null ? new Bundles(1, 200) : bundles;
    gradleJavaMin = gradleJavaMin == null ? 8 : gradleJavaMin;
    gradleJavaMax = gradleJavaMax == null ? 25 : gradleJavaMax;
    buildLogMaxLines = buildLogMaxLines == null ? 200 : buildLogMaxLines;
    jdkSearchPaths = jdkSearchPaths == null
            ? List.of(
                    Path.of(System.getProperty("user.home"), ".sdkman/candidates/java"),
                    Path.of("/usr/lib/jvm"))
            : List.copyOf(jdkSearchPaths);
    jdkHomeEnvVar = jdkHomeEnvVar == null || jdkHomeEnvVar.isBlank()
            ? "DSL_BUILDER_JAVA_HOME"
            : jdkHomeEnvVar;
    allowedRepoSchemes = allowedRepoSchemes == null
            ? List.of("https")
            : List.copyOf(allowedRepoSchemes);
    allowedRepoHosts = allowedRepoHosts == null ? List.of() : List.copyOf(allowedRepoHosts);
  }

  /**
   * Convenience factory for tests that only need a workspace directory. All nested configuration
   * objects receive their defaults, and scalar fields receive sensible test values.
   */
  public static DslBuilderProperties defaultsWithWorkspace(Path workspaceDir) {
    return new DslBuilderProperties(workspaceDir, Duration.ofMinutes(10), Duration.ofHours(1),
            null, "0.0.1-SNAPSHOT", "1.27.0", "4.0.4", "v1", List.of("clean", "build"),
            List.of("dsl", "models"), "project/templates", null, null, null, null, null,
            new Bundles(1, 200), 8, 25, 200, null, null, null, false, null);
  }

  public DslBuilderProperties withQueue(Queue queue) {
    return new DslBuilderProperties(workspaceDir, cleanupInterval, sessionTtl, gradleJavaHome,
            dslVersion, temporalVersion, springBootVersion, defaultBuildVersion, buildTasks,
            sourceFolders, templatesDir, sourceDir, queue, files, git, fileBuffer, bundles,
            gradleJavaMin, gradleJavaMax, buildLogMaxLines, jdkSearchPaths, jdkHomeEnvVar,
            allowedRepoSchemes, allowPlainHttpRepo, allowedRepoHosts);
  }

  public DslBuilderProperties withFiles(Files files) {
    return new DslBuilderProperties(workspaceDir, cleanupInterval, sessionTtl, gradleJavaHome,
            dslVersion, temporalVersion, springBootVersion, defaultBuildVersion, buildTasks,
            sourceFolders, templatesDir, sourceDir, queue, files, git, fileBuffer, bundles,
            gradleJavaMin, gradleJavaMax, buildLogMaxLines, jdkSearchPaths, jdkHomeEnvVar,
            allowedRepoSchemes, allowPlainHttpRepo, allowedRepoHosts);
  }

  public DslBuilderProperties withGit(Git git) {
    return new DslBuilderProperties(workspaceDir, cleanupInterval, sessionTtl, gradleJavaHome,
            dslVersion, temporalVersion, springBootVersion, defaultBuildVersion, buildTasks,
            sourceFolders, templatesDir, sourceDir, queue, files, git, fileBuffer, bundles,
            gradleJavaMin, gradleJavaMax, buildLogMaxLines, jdkSearchPaths, jdkHomeEnvVar,
            allowedRepoSchemes, allowPlainHttpRepo, allowedRepoHosts);
  }

  public DslBuilderProperties withFileBuffer(FileBuffer fileBuffer) {
    return new DslBuilderProperties(workspaceDir, cleanupInterval, sessionTtl, gradleJavaHome,
            dslVersion, temporalVersion, springBootVersion, defaultBuildVersion, buildTasks,
            sourceFolders, templatesDir, sourceDir, queue, files, git, fileBuffer, bundles,
            gradleJavaMin, gradleJavaMax, buildLogMaxLines, jdkSearchPaths, jdkHomeEnvVar,
            allowedRepoSchemes, allowPlainHttpRepo, allowedRepoHosts);
  }

  public record Queue(
          @DefaultValue("100") int capacity,
          @DefaultValue("4") int workers) {
  }

  public record Files(
          @DefaultValue("5") int flushIntervalSeconds,
          @DefaultValue("100") int maxQueueSize,
          @DefaultValue("32") int readBulkheadPermits,
          @DefaultValue("8") int writeBulkheadPermits,
          @DefaultValue("5") long acquireTimeoutSeconds) {
  }

  public record Git(
          @DefaultValue("true") boolean enabled,
          String repositoryDir,
          String worktreesDir,
          String repoUrl,
          String subPath,
          String branch,
          @DefaultValue("5") int statusCacheTtlSeconds,
          @DefaultValue("false") boolean pushOnCommit,
          @DefaultValue("origin") String remote) {
  }

  public record FileBuffer(
          @DefaultValue("1000") int maxEntries,
          @DefaultValue("3600") long expireAfterWriteSeconds) {
  }

  public record Bundles(
          @DefaultValue("1") int bundleFormatVersion,
          @DefaultValue("200") int bundleMaxDefinitions) {
  }
}
