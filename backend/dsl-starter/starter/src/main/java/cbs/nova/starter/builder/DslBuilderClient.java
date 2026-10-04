package cbs.nova.starter.builder;

import cbs.nova.starter.exception.BuilderApiException;

import cbs.nova.starter.model.CompileModels.CompileRequest;
import cbs.nova.starter.model.CompileModels.CompileResult;
import cbs.nova.starter.model.DslFileModels.BulkWriteRequest;
import cbs.nova.starter.model.DslFileModels.BulkWriteResult;
import cbs.nova.starter.model.DslFileModels.FileContentRequest;
import cbs.nova.starter.model.DslFileModels.FileContentResponse;
import cbs.nova.starter.model.DslFileModels.FileEntry;
import cbs.nova.starter.model.DslFileModels.FlushResult;
import cbs.nova.starter.model.DslFileModels.PendingWritesStatus;
import cbs.nova.starter.model.FileEntityKind;
import cbs.nova.starter.model.VcsModels.CommitRequest;
import cbs.nova.starter.model.VcsModels.CommitResult;
import cbs.nova.starter.model.VcsModels.DiscardRequest;
import cbs.nova.starter.model.VcsModels.DiscardResult;
import cbs.nova.starter.model.VcsModels.FetchResult;
import cbs.nova.starter.model.VcsModels.LogEntry;
import cbs.nova.starter.model.VcsModels.PullResult;
import cbs.nova.starter.model.VcsModels.RebaseResult;
import cbs.nova.starter.model.VcsModels.WorkspaceInfo;
import cbs.nova.dsl.vcs.RepoStatus;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

public class DslBuilderClient {

  private static final ParameterizedTypeReference<List<FileEntry>> FILE_ENTRIES_TYPE = new ParameterizedTypeReference<>() {
  };
  private static final ParameterizedTypeReference<List<LogEntry>> LOG_ENTRY_TYPE = new ParameterizedTypeReference<>() {
  };

  private final RestClient restClient;
  private final BuilderRequestExecutor executor;
  private final BuilderCache cache;

  public DslBuilderClient(RestClient restClient, ThreadPoolBulkhead queue, Bulkhead bulkhead,
          CircuitBreaker breaker, BuilderCache cache) {
    this.restClient = restClient;
    this.executor = new BuilderRequestExecutor(queue, bulkhead, breaker);
    this.cache = cache;
  }

  public CompileResult compile(CompileRequest request) {
    return execute(() -> doCompile(request));
  }

  public byte[] downloadZip(String id) {
    return execute(() -> doDownloadZip(id));
  }

  public List<FileEntry> listFiles(String prefix) {
    return listFiles(prefix, FileEntityKind.DSL);
  }

  public List<FileEntry> listFiles(String prefix, FileEntityKind kind) {
    return cache.listFiles(cacheKey(prefix, kind), () -> execute(() -> doListFiles(prefix, kind)));
  }

  public FileContentResponse readFile(String path) {
    return readFile(path, FileEntityKind.DSL);
  }

  public FileContentResponse readFile(String path, FileEntityKind kind) {
    return cache.readFile(cacheKey(path, kind), () -> execute(() -> doReadFile(path, kind)));
  }

  public FileContentResponse readFileByName(String name) {
    return readFileByName(name, FileEntityKind.DSL);
  }

  public FileContentResponse readFileByName(String name, FileEntityKind kind) {
    return cache.readFileByName(cacheKey(name, kind),
            () -> execute(() -> doReadFileByName(name, kind)));
  }

  public boolean fileExists(String path) {
    return fileExists(path, FileEntityKind.DSL);
  }

  public boolean fileExists(String path, FileEntityKind kind) {
    return cache.fileExists(cacheKey(path, kind), () -> execute(() -> doFileExists(path, kind)));
  }

  public void stageWrite(String path, String content) {
    stageWrite(path, content, FileEntityKind.DSL);
  }

  public void stageWrite(String path, String content, FileEntityKind kind) {
    execute(() -> doStageWrite(path, content, kind));
    cache.invalidateFile(path);
    cache.invalidateVcsStatus();
  }

  public FileContentResponse stageWriteByName(String name, String content) {
    return stageWriteByName(name, content, FileEntityKind.DSL);
  }

  public FileContentResponse stageWriteByName(String name, String content, FileEntityKind kind) {
    FileContentResponse result = execute(() -> doStageWriteByName(name, content, kind));
    cache.invalidateFileByName(cacheKey(name, kind), result == null ? null : result.path());
    cache.invalidateVcsStatus();
    return result;
  }

  public BulkWriteResult stageAll(List<FileContentRequest> files) {
    return stageAll(files, FileEntityKind.DSL);
  }

  public BulkWriteResult stageAll(List<FileContentRequest> files, FileEntityKind kind) {
    BulkWriteResult result = execute(() -> doStageAll(files, kind));
    cache.invalidateFiles();
    cache.invalidateVcsStatus();
    return result;
  }

  public FlushResult flushFiles() {
    return flushFiles(FileEntityKind.DSL);
  }

  public FlushResult flushFiles(FileEntityKind kind) {
    FlushResult result = execute(() -> doFlushFiles(kind));
    cache.invalidateFiles();
    cache.invalidateVcsStatus();
    return result;
  }

  public int pendingCount() {
    return pendingCount(FileEntityKind.DSL);
  }

  public int pendingCount(FileEntityKind kind) {
    return execute(() -> doPendingCount(kind));
  }

  public Optional<RepoStatus> vcsStatus() {
    return cache.vcsStatus(() -> {
      try {
        return Optional.of(execute(this::doVcsStatus));
      } catch (BuilderApiException e) {
        if (e.getStatusCode().value() == 404) {
          return Optional.empty();
        }
        throw e;
      }
    });
  }

  public CommitResult commit(CommitRequest request) {
    CommitResult result = execute(() -> doCommit(request));
    cache.invalidateFiles();
    cache.invalidateVcsStatus();
    cache.invalidateVcsLog();
    return result;
  }

  public DiscardResult discard(DiscardRequest request) {
    DiscardResult result = execute(() -> doDiscard(request));
    cache.invalidateFiles();
    cache.invalidateVcsStatus();
    cache.invalidateVcsLog();
    return result;
  }

  /**
   * Fetch the builder workspace's configured remote. Read-only for the work tree, so only the
   * cached VCS status is invalidated.
   */
  public FetchResult vcsFetch() {
    FetchResult result = execute(this::doVcsFetch);
    cache.invalidateVcsStatus();
    return result;
  }

  /** Pull the builder workspace per its configured pull strategy. */
  public PullResult vcsPull() {
    PullResult result = execute(this::doVcsPull);
    cache.invalidateFiles();
    cache.invalidateVcsStatus();
    cache.invalidateVcsLog();
    return result;
  }

  /** Rebase the builder workspace branch onto its upstream. */
  public RebaseResult vcsRebase() {
    RebaseResult result = execute(this::doVcsRebase);
    cache.invalidateFiles();
    cache.invalidateVcsStatus();
    cache.invalidateVcsLog();
    return result;
  }

  public List<LogEntry> vcsLog(String path, int limit) {
    return cache.vcsLog(path, limit, () -> execute(() -> doLog(path, limit)));
  }

  public String vcsShow(String path, String commitId) {
    return cache.vcsShow(path, commitId,
            () -> execute(() -> doShow(path, commitId)));
  }

  /**
   * Current branch of the workspace git repo. Cached like other VCS reads; invalidated whenever the
   * cache is invalidated (commits, discards, etc.). Returns {@code null} when git is disabled or no
   * repository is configured.
   */
  public String vcsBranch() {
    return cache.vcsBranch(() -> {
      var response = restClient.get().uri("/api/dsl/vcs/branch")
              .retrieve()
              .body(Map.class);
      if (response == null) {
        return null;
      }
      Object branch = response.get("branch");
      return branch == null ? null : branch.toString();
    });
  }

  /**
   * Workspace mode and actual work tree of the connected dsl-builder
   * ({@code GET /api/dsl/workspace}). Never cached: the builder is the source of truth for the
   * workspace, and this call is only made from the metadata endpoint.
   */
  public WorkspaceInfo workspace() {
    return execute(this::doWorkspace);
  }

  private CompileResult doCompile(CompileRequest request) {
    return restClient.post().uri("/api/dsl/compile").body(request).retrieve()
            .body(CompileResult.class);
  }

  private byte[] doDownloadZip(String id) {
    return restClient.get().uri("/api/dsl/compile/{id}/download", id).retrieve()
            .body(byte[].class);
  }

  private List<FileEntry> doListFiles(String prefix, FileEntityKind kind) {
    return restClient.get().uri(uriBuilder -> {
      var builder = uriBuilder.path("/api/dsl/files");
      if (kind != null && kind != FileEntityKind.DSL) {
        builder.queryParam("kind", kind.name());
      }
      return prefix == null || prefix.isBlank()
              ? builder.build()
              : builder.queryParam("prefix", prefix).build();
    }).retrieve().body(FILE_ENTRIES_TYPE);
  }

  private FileContentResponse doReadFile(String path, FileEntityKind kind) {
    return restClient.get().uri(uriBuilder -> fileUri(uriBuilder, path, kind)).retrieve()
            .body(FileContentResponse.class);
  }

  private FileContentResponse doReadFileByName(String name, FileEntityKind kind) {
    return restClient.get().uri(uriBuilder -> fileByNameUri(uriBuilder, name, kind)).retrieve()
            .body(FileContentResponse.class);
  }

  private boolean doFileExists(String path, FileEntityKind kind) {
    Boolean exists = restClient.get()
            .uri(uriBuilder -> uriBuilder.path("/api/dsl/files/exists/")
                    .pathSegment(path.split("/"))
                    .queryParamIfPresent("kind", Optional.ofNullable(kind))
                    .build())
            .retrieve().body(Boolean.class);
    return Boolean.TRUE.equals(exists);
  }

  private Void doStageWrite(String path, String content, FileEntityKind kind) {
    return restClient.post().uri(uriBuilder -> fileUri(uriBuilder, path, kind)).body(content)
            .retrieve()
            .body(Void.class);
  }

  private FileContentResponse doStageWriteByName(String name, String content,
          FileEntityKind kind) {
    return restClient.post().uri(uriBuilder -> fileByNameUri(uriBuilder, name, kind)).body(content)
            .retrieve().body(FileContentResponse.class);
  }

  private BulkWriteResult doStageAll(List<FileContentRequest> files, FileEntityKind kind) {
    return restClient.post().uri(uriBuilder -> {
      var builder = uriBuilder.path("/api/dsl/files/bulk");
      if (kind != null && kind != FileEntityKind.DSL) {
        builder.queryParam("kind", kind.name());
      }
      return builder.build();
    }).body(new BulkWriteRequest(files)).retrieve().body(BulkWriteResult.class);
  }

  private FlushResult doFlushFiles(FileEntityKind kind) {
    return restClient.post().uri(uriBuilder -> {
      var builder = uriBuilder.path("/api/dsl/files/flush");
      if (kind != null && kind != FileEntityKind.DSL) {
        builder.queryParam("kind", kind.name());
      }
      return builder.build();
    }).retrieve().body(FlushResult.class);
  }

  private Integer doPendingCount(FileEntityKind kind) {
    var status = restClient.get().uri(uriBuilder -> {
      var builder = uriBuilder.path("/api/dsl/files/status");
      if (kind != null && kind != FileEntityKind.DSL) {
        builder.queryParam("kind", kind.name());
      }
      return builder.build();
    }).retrieve().body(PendingWritesStatus.class);
    return status == null ? 0 : status.pending();
  }

  private RepoStatus doVcsStatus() {
    return restClient.get().uri("/api/dsl/vcs/status").retrieve().body(RepoStatus.class);
  }

  private WorkspaceInfo doWorkspace() {
    return restClient.get().uri("/api/dsl/workspace").retrieve().body(WorkspaceInfo.class);
  }

  private CommitResult doCommit(CommitRequest request) {
    return restClient.post().uri("/api/dsl/vcs/commit").body(request).retrieve()
            .body(CommitResult.class);
  }

  private DiscardResult doDiscard(DiscardRequest request) {
    return restClient.post().uri("/api/dsl/vcs/discard").body(request).retrieve()
            .body(DiscardResult.class);
  }

  private FetchResult doVcsFetch() {
    return restClient.post().uri("/api/dsl/vcs/fetch").retrieve().body(FetchResult.class);
  }

  private PullResult doVcsPull() {
    return restClient.post().uri("/api/dsl/vcs/pull").retrieve().body(PullResult.class);
  }

  private RebaseResult doVcsRebase() {
    return restClient.post().uri("/api/dsl/vcs/rebase").retrieve().body(RebaseResult.class);
  }

  private List<LogEntry> doLog(String path, int limit) {
    return restClient.get()
            .uri(uriBuilder -> uriBuilder.path("/api/dsl/vcs/log")
                    .queryParam("path", path)
                    .queryParam("limit", limit)
                    .build())
            .retrieve().body(LOG_ENTRY_TYPE);
  }

  private String doShow(String path, String commitId) {
    return restClient.get()
            .uri(uriBuilder -> uriBuilder.path("/api/dsl/vcs/show")
                    .queryParam("path", path)
                    .queryParam("commit", commitId)
                    .build())
            .retrieve().body(String.class);
  }

  private static URI fileUri(UriBuilder uriBuilder, String path, FileEntityKind kind) {
    var builder = uriBuilder.path("/api/dsl/files/").pathSegment(path.split("/"));
    if (kind != null && kind != FileEntityKind.DSL) {
      builder.queryParam("kind", kind.name());
    }
    return builder.build();
  }

  private static URI fileByNameUri(UriBuilder uriBuilder, String name, FileEntityKind kind) {
    var builder = uriBuilder.path("/api/dsl/files/by-name/").pathSegment(name);
    if (kind != null && kind != FileEntityKind.DSL) {
      builder.queryParam("kind", kind.name());
    }
    return builder.build();
  }

  private static String cacheKey(String key, FileEntityKind kind) {
    return kind == null || kind == FileEntityKind.DSL ? key : kind.name() + ":" + key;
  }

  private <T> T execute(Callable<T> call) {
    return executor.execute(call);
  }

  <T> CompletionStage<T> submit(Callable<T> call) {
    return executor.submit(call);
  }

}
