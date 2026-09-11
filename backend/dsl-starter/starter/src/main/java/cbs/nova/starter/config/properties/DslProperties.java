package cbs.nova.starter.config.properties;

import jakarta.validation.Valid;
import lombok.Builder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the DSL runtime ({@code cbs.dsl.*}).
 *
 * <p>
 * Constructor-bound record: scalar defaults are declared with {@link DefaultValue} for the Spring
 * binder and re-applied in the compact constructor, so direct construction (canonical constructor
 * or the Lombok-generated {@link Builder}) yields the same defaults. Scalar components are boxed so
 * the compact constructor can tell "not set" ({@code null}) apart from an explicit value — records
 * have no Lombok {@code @Builder.Default} support. Nested-object defaults follow the
 * {@link DryRunProperties} idiom: {@code @DefaultValue} on the component plus null-coalescing in
 * the compact constructor.
 */
@Builder
@ConfigurationProperties(prefix = "cbs.dsl")
@Validated
public record DslProperties(
        String sourceDir,
        /**
         * Workbench virtual-workspace root for filesystem-backed drafts. Relative values resolve
         * against {@code sourceDir}; an absolute value is used as-is.
         */
        @DefaultValue(".workbench/drafts-fs") String workbenchWorkspaceRoot,
        @DefaultValue("dsl-task-queue") String taskQueue,
        @Valid @DefaultValue Worker worker,
        @Valid @DefaultValue Reload reload,
        @Valid @DefaultValue Auth auth,
        @Valid @DefaultValue Drafts drafts,
        @Valid @DefaultValue Files files,
        @Valid @DefaultValue Git git,
        @Valid @DefaultValue FileBuffer fileBuffer,
        @Valid @DefaultValue Bundles bundles) {

  public DslProperties {
    workbenchWorkspaceRoot = workbenchWorkspaceRoot == null
            ? ".workbench/drafts-fs"
            : workbenchWorkspaceRoot;
    taskQueue = taskQueue == null ? "dsl-task-queue" : taskQueue;
    worker = worker == null ? new Worker(false) : worker;
    reload = reload == null ? new Reload(false) : reload;
    auth = auth == null ? new Auth(false, null, new Rbac(false, "roles")) : auth;
    drafts = drafts == null ? new Drafts(20) : drafts;
    files = files == null ? new Files(true, 5, 100, 32, 8, 5L) : files;
    git = git == null ? new Git(true, null, 5) : git;
    fileBuffer = fileBuffer == null ? new FileBuffer(1000, 3600L) : fileBuffer;
    bundles = bundles == null ? new Bundles(false) : bundles;
  }

  /**
   * Properties for {@link cbs.nova.starter.service.DslDefinitionBundleService} used outside Spring
   * (tests): default bundle policy (digest not required), blank source dir.
   */
  public static DslProperties bundleServiceDefaults() {
    return builder().sourceDir("").build();
  }

  @Builder
  public record Worker(@DefaultValue("false") Boolean enabled) {

    public Worker {
      enabled = enabled == null ? false : enabled;
    }
  }

  @Builder
  public record Reload(@DefaultValue("false") Boolean enabled) {

    public Reload {
      enabled = enabled == null ? false : enabled;
    }
  }

  @Builder
  public record Auth(
          @DefaultValue("false") Boolean enabled,
          String apiKey,
          @Valid @DefaultValue Rbac rbac) {

    public Auth {
      enabled = enabled == null ? false : enabled;
      rbac = rbac == null ? new Rbac(false, "roles") : rbac;
    }
  }

  /**
   * Role-based access control (RBAC) sub-feature of {@code cbs.dsl.auth} (T408, phase 1). When
   * {@code cbs.dsl.auth.rbac.enabled=true} the starter registers a servlet filter that enforces a
   * per-route {@link cbs.nova.starter.security.Role} table on every {@code /api/**} request. The
   * filter is OFF by default; existing deployments see zero behaviour change.
   *
   * @param enabled
   *          whether the RBAC filter is registered.
   * @param claim
   *          the JWT claim name to read roles from when an OIDC principal is authenticated. Default
   *          {@code "roles"}. Standard OAuth 2.0 {@code "scope"} / {@code "scp"} claims are also
   *          consulted when the default claim is in use.
   */
  @Builder
  public record Rbac(
          @DefaultValue("false") Boolean enabled,
          @DefaultValue("roles") String claim) {

    public Rbac {
      enabled = enabled == null ? false : enabled;
      claim = (claim == null || claim.isBlank()) ? "roles" : claim;
    }
  }

  @Builder
  public record Drafts(
          /**
           * How many prior published snapshots to keep per definition. Older snapshots are pruned
           * on publish; values less than or equal to 0 keep an unlimited history.
           */
          @DefaultValue("20") Integer historyLimit) {

    public Drafts {
      historyLimit = historyLimit == null ? 20 : historyLimit;
    }
  }

  @Builder
  public record Files(
          @DefaultValue("true") Boolean enabled,

          /**
           * Seconds between automatic flushes of the staged write buffer. Zero or negative disables
           * background flushing; call POST /api/dsl/files/flush explicitly.
           */
          @DefaultValue("5") Integer flushIntervalSeconds,

          /**
           * Maximum number of staged writes before an automatic flush is triggered.
           */
          @DefaultValue("100") Integer maxQueueSize,

          /**
           * Maximum concurrent file read operations.
           */
          @DefaultValue("32") Integer readBulkheadPermits,

          /**
           * Maximum concurrent file write operations.
           */
          @DefaultValue("8") Integer writeBulkheadPermits,

          /**
           * Seconds to wait for a bulkhead permit before failing the file operation.
           */
          @DefaultValue("5") Long acquireTimeoutSeconds) {

    public Files {
      enabled = enabled == null ? true : enabled;
      flushIntervalSeconds = flushIntervalSeconds == null ? 5 : flushIntervalSeconds;
      maxQueueSize = maxQueueSize == null ? 100 : maxQueueSize;
      readBulkheadPermits = readBulkheadPermits == null ? 32 : readBulkheadPermits;
      writeBulkheadPermits = writeBulkheadPermits == null ? 8 : writeBulkheadPermits;
      acquireTimeoutSeconds = acquireTimeoutSeconds == null ? 5L : acquireTimeoutSeconds;
    }
  }

  @Builder
  public record Git(
          /**
           * Whether to inspect the DSL source directory as a Git working tree when resolving
           * definition statuses. If disabled or if no repository is found, status falls back to
           * filesystem markers only.
           */
          @DefaultValue("true") Boolean enabled,

          /**
           * Root directory of the Git repository to inspect. Defaults to {@code dsl.source-dir}.
           */
          String repositoryDir,

          /**
           * How long to cache the result of a Git status call, in seconds. A small TTL avoids
           * re-scanning the repository on every introspection request while still reflecting recent
           * edits promptly.
           */
          @DefaultValue("5") Integer statusCacheTtlSeconds) {

    public Git {
      enabled = enabled == null ? true : enabled;
      statusCacheTtlSeconds = statusCacheTtlSeconds == null ? 5 : statusCacheTtlSeconds;
    }
  }

  @Builder
  public record FileBuffer(
          /**
           * Maximum number of staged-but-undrained file entries kept in memory. Oldest entries are
           * evicted once this bound is exceeded, preventing unbounded heap growth from abandoned
           * stage-without-drain flows.
           */
          @DefaultValue("1000") Integer maxEntries,

          /**
           * How long a staged entry survives without being drained before it is evicted, in
           * seconds.
           */
          @DefaultValue("3600") Long expireAfterWriteSeconds) {

    public FileBuffer {
      maxEntries = maxEntries == null ? 1000 : maxEntries;
      expireAfterWriteSeconds = expireAfterWriteSeconds == null ? 3600L : expireAfterWriteSeconds;
    }
  }

  @Builder
  public record Bundles(
          /**
           * Whether imported bundles must carry a digest. When true, bundles without a digest are
           * rejected with BUNDLE_DIGEST_MISSING.
           */
          @DefaultValue("false") Boolean requireDigest) {

    public Bundles {
      requireDigest = requireDigest == null ? false : requireDigest;
    }
  }
}
