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
        @DefaultValue Drafts drafts,
        @DefaultValue Files files,
        @DefaultValue Git git,
        @DefaultValue FileBuffer fileBuffer) {

  public DslBuilderProperties {
    queue = queue == null ? new Queue(100, 4) : queue;
    drafts = drafts == null ? new Drafts(20) : drafts;
    files = files == null ? new Files(5, 100, 32, 8, 5L) : files;
    git = git == null ? new Git(true, null, null, null, null, null, 5) : git;
    fileBuffer = fileBuffer == null ? new FileBuffer(1000, 3600L) : fileBuffer;
  }

  public record Queue(
          @DefaultValue("100") int capacity,
          @DefaultValue("4") int workers) {
  }

  public record Drafts(
          @DefaultValue("20") int historyLimit) {
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
          @DefaultValue("5") int statusCacheTtlSeconds) {
  }

  public record FileBuffer(
          @DefaultValue("1000") int maxEntries,
          @DefaultValue("3600") long expireAfterWriteSeconds) {
  }
}
