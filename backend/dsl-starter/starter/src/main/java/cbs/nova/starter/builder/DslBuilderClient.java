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

  // TODO: replace ctor with lombok's one
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
    return cache.listFiles(prefix, () -> execute(() -> doListFiles(prefix)));
  }

  public FileContentResponse readFile(String path) {
    return cache.readFile(path, () -> execute(() -> doReadFile(path)));
  }

  public boolean fileExists(String path) {
    return cache.fileExists(path, () -> execute(() -> doFileExists(path)));
  }

  public void stageWrite(String path, String content) {
    execute(() -> doStageWrite(path, content));
    cache.invalidateFile(path);
    cache.invalidateVcsStatus();
  }

  public BulkWriteResult stageAll(List<FileContentRequest> files) {
    BulkWriteResult result = execute(() -> doStageAll(files));
    cache.invalidateFiles();
    cache.invalidateVcsStatus();
    return result;
  }

  public FlushResult flushFiles() {
    FlushResult result = execute(this::doFlushFiles);
    cache.invalidateFiles();
    cache.invalidateVcsStatus();
    return result;
  }

  public int pendingCount() {
    return execute(this::doPendingCount);
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

  private List<FileEntry> doListFiles(String prefix) {
    return restClient.get().uri(uriBuilder -> {
      var builder = uriBuilder.path("/api/dsl/files");
      return prefix == null || prefix.isBlank()
              ? builder.build()
              : builder.queryParam("prefix", prefix).build();
    }).retrieve().body(FILE_ENTRIES_TYPE);
  }

  private FileContentResponse doReadFile(String path) {
    return restClient.get().uri(uriBuilder -> fileUri(uriBuilder, path)).retrieve()
            .body(FileContentResponse.class);
  }

  private boolean doFileExists(String path) {
    Boolean exists = restClient.get()
            .uri(uriBuilder -> uriBuilder.path("/api/dsl/files/exists/")
                    .pathSegment(path.split("/")).build())
            .retrieve().body(Boolean.class);
    return Boolean.TRUE.equals(exists);
  }

  private Void doStageWrite(String path, String content) {
    return restClient.post().uri(uriBuilder -> fileUri(uriBuilder, path)).body(content).retrieve()
            .body(Void.class);
  }

  private BulkWriteResult doStageAll(List<FileContentRequest> files) {
    return restClient.post().uri("/api/dsl/files/bulk")
            .body(new BulkWriteRequest(files)).retrieve().body(BulkWriteResult.class);
  }

  private FlushResult doFlushFiles() {
    return restClient.post().uri("/api/dsl/files/flush").retrieve().body(FlushResult.class);
  }

  private Integer doPendingCount() {
    var status = restClient.get().uri("/api/dsl/files/status").retrieve()
            .body(PendingWritesStatus.class);
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

  private static URI fileUri(UriBuilder uriBuilder, String path) {
    return uriBuilder.path("/api/dsl/files/").pathSegment(path.split("/")).build();
  }

  private <T> T execute(Callable<T> call) {
    return executor.execute(call);
  }

  <T> CompletionStage<T> submit(Callable<T> call) {
    return executor.submit(call);
  }

}
