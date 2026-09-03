package cbs.nova.starter.config.properties;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

//TODO: redo to a record(e.g. check git history)
@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@ConfigurationProperties(prefix = "cbs.dsl")
public class DslProperties {

  private String sourceDir;
  private String taskQueue = "dsl-task-queue";
  private Worker worker = new Worker();
  private Reload reload = new Reload();
  private Auth auth = new Auth();
  private Drafts drafts = new Drafts();
  private Files files = new Files();
  private Git git = new Git();

  @Data
  public static class Worker {

    private boolean enabled;

  }

  @Data
  public static class Reload {

    private boolean enabled;

  }

  @Data
  public static class Auth {

    private String apiKey;

  }

  @Data
  public static class Drafts {

    /**
     * How many prior published snapshots to keep per definition. Older snapshots are pruned on
     * publish; values less than or equal to 0 keep an unlimited history.
     */
    private int historyLimit = 20;

  }

  @Data
  public static class Files {

    private boolean enabled = true;

    /**
     * Seconds between automatic flushes of the staged write buffer. Zero or negative disables
     * background flushing; call POST /api/dsl/files/flush explicitly.
     */
    private int flushIntervalSeconds = 5;

    /**
     * Maximum number of staged writes before an automatic flush is triggered.
     */
    private int maxQueueSize = 100;

    /**
     * Maximum concurrent file read operations.
     */
    private int readBulkheadPermits = 32;

    /**
     * Maximum concurrent file write operations.
     */
    private int writeBulkheadPermits = 8;
  }

  @Data
  public static class Git {

    /**
     * Whether to inspect the DSL source directory as a Git working tree when resolving definition
     * statuses. If disabled or if no repository is found, status falls back to filesystem markers
     * only.
     */
    private boolean enabled = true;

    /**
     * Root directory of the Git repository to inspect. Defaults to {@code dsl.source-dir}.
     */
    private String repositoryDir;

    /**
     * How long to cache the result of a Git status call, in seconds. A small TTL avoids re-scanning
     * the repository on every introspection request while still reflecting recent edits promptly.
     */
    private int statusCacheTtlSeconds = 5;

  }
}
