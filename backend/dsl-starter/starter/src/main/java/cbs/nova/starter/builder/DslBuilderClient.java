package cbs.nova.starter.builder;

import cbs.nova.starter.exception.BuilderApiException;
import cbs.nova.starter.exception.BuilderClientBusyException;
import cbs.nova.starter.exception.BuilderUnavailableException;
import cbs.nova.starter.model.CompileModels.CompileRequest;
import cbs.nova.starter.model.CompileModels.CompileResult;
import cbs.nova.starter.model.DslFileModels.BulkWriteRequest;
import cbs.nova.starter.model.DslFileModels.BulkWriteResult;
import cbs.nova.starter.model.DslFileModels.FileContentRequest;
import cbs.nova.starter.model.DslFileModels.FileContentResponse;
import cbs.nova.starter.model.DslFileModels.FileEntry;
import cbs.nova.starter.model.DslFileModels.FlushResult;
import cbs.nova.starter.model.DslFileModels.PendingWritesStatus;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.model.VcsModels.DefinitionBundle;
import cbs.nova.starter.model.VcsModels.DefinitionHistoryEntry;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import cbs.nova.starter.model.VcsModels.DraftResponse;
import cbs.nova.starter.model.VcsModels.DraftSummary;
import cbs.nova.starter.model.VcsModels.HistoryDiffResponse;
import cbs.nova.starter.model.VcsModels.ImportBundleResult;
import cbs.nova.starter.service.DslGitStatusResolver.RepoStatus;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.util.UriBuilder;

@RequiredArgsConstructor
public class DslBuilderClient {

  private static final ParameterizedTypeReference<List<DefinitionHistoryEntry>> HISTORY_TYPE = new ParameterizedTypeReference<>() {
  };
  private static final ParameterizedTypeReference<List<FileEntry>> FILE_ENTRIES_TYPE = new ParameterizedTypeReference<>() {
  };

  private final RestClient restClient;
  private final ThreadPoolBulkhead queue;
  private final Bulkhead bulkhead;
  private final CircuitBreaker breaker;
  private final BuilderCache cache;

  public CompileResult compile(CompileRequest request) {
    return execute(() -> doCompile(request));
  }

  public byte[] downloadZip(String id) {
    return execute(() -> doDownloadZip(id));
  }

  public DraftResponse saveDraft(String name, DraftRequest body) {
    return execute(() -> doSaveDraft(name, body));
  }

  public DraftResponse publishDraft(String name, DraftRequest body) {
    return execute(() -> doPublishDraft(name, body));
  }

  public List<DefinitionHistoryEntry> history(String name) {
    return cache.history(name, () -> execute(() -> doHistory(name)));
  }

  public DraftRequest historyEntry(String name, String timestamp) {
    return cache.historyEntry(name, timestamp,
            () -> execute(() -> doHistoryEntry(name, timestamp)));
  }

  public HistoryDiffResponse historyDiff(String name, String timestamp) {
    return cache.historyDiff(name, timestamp,
            () -> execute(() -> doHistoryDiff(name, timestamp)));
  }

  public DraftResponse restoreDraft(String name, String timestamp) {
    return execute(() -> doRestoreDraft(name, timestamp));
  }

  public DraftResponse deleteDraft(String name) {
    return execute(() -> doDeleteDraft(name));
  }

  public PageResponse<DraftSummary> listDrafts(int limit, int offset) {
    return cache.listDrafts(limit, offset, () -> execute(() -> doListDrafts(limit, offset)));
  }

  public DraftRequest readDraft(String name) {
    return cache.readDraft(name, () -> execute(() -> doReadDraft(name)));
  }

  public DefinitionBundle exportBundle(boolean includeDrafts) {
    return cache.exportBundle(includeDrafts, () -> execute(() -> doExportBundle(includeDrafts)));
  }

  public ImportBundleResult importBundle(DefinitionBundle bundle, boolean dryRun) {
    return execute(() -> doImportBundle(bundle, dryRun));
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
  }

  public BulkWriteResult stageAll(List<FileContentRequest> files) {
    return execute(() -> doStageAll(files));
  }

  public FlushResult flushFiles() {
    return execute(this::doFlushFiles);
  }

  public int pendingCount() {
    return cache.pendingCount(() -> execute(this::doPendingCount));
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

  private CompileResult doCompile(CompileRequest request) {
    return restClient.post().uri("/api/dsl/compile").body(request).retrieve()
            .body(CompileResult.class);
  }

  private byte[] doDownloadZip(String id) {
    return restClient.get().uri("/api/dsl/compile/{id}/download", id).retrieve()
            .body(byte[].class);
  }

  private DraftResponse doSaveDraft(String name, DraftRequest body) {
    return restClient.post().uri("/api/dsl/drafts/{name}/save", name).body(body).retrieve()
            .body(DraftResponse.class);
  }

  private DraftResponse doPublishDraft(String name, DraftRequest body) {
    return restClient.post().uri("/api/dsl/drafts/{name}/publish", name).body(body).retrieve()
            .body(DraftResponse.class);
  }

  private List<DefinitionHistoryEntry> doHistory(String name) {
    return restClient.get().uri("/api/dsl/drafts/{name}/history", name).retrieve()
            .body(HISTORY_TYPE);
  }

  private DraftRequest doHistoryEntry(String name, String timestamp) {
    return restClient.get()
            .uri("/api/dsl/drafts/{name}/history/{timestamp}", name, timestamp)
            .retrieve().body(DraftRequest.class);
  }

  private HistoryDiffResponse doHistoryDiff(String name, String timestamp) {
    return restClient.get()
            .uri("/api/dsl/drafts/{name}/history/{timestamp}/diff", name, timestamp)
            .retrieve().body(HistoryDiffResponse.class);
  }

  private DraftResponse doRestoreDraft(String name, String timestamp) {
    return restClient.post()
            .uri("/api/dsl/drafts/{name}/history/{timestamp}/restore", name, timestamp)
            .retrieve().body(DraftResponse.class);
  }

  private DraftResponse doDeleteDraft(String name) {
    return restClient.delete().uri("/api/dsl/drafts/{name}", name).retrieve()
            .body(DraftResponse.class);
  }

  private PageResponse<DraftSummary> doListDrafts(int limit, int offset) {
    return restClient.get()
            .uri("/api/dsl/drafts?limit={limit}&offset={offset}", limit, offset)
            .retrieve().body(new ParameterizedTypeReference<>() {
            });
  }

  private DraftRequest doReadDraft(String name) {
    return restClient.get().uri("/api/dsl/drafts/{name}", name).retrieve()
            .body(DraftRequest.class);
  }

  private DefinitionBundle doExportBundle(boolean includeDrafts) {
    return restClient.get()
            .uri("/api/dsl/definitions/export" + (includeDrafts ? "?include=drafts" : ""))
            .retrieve().body(DefinitionBundle.class);
  }

  private ImportBundleResult doImportBundle(DefinitionBundle bundle, boolean dryRun) {
    return restClient.post()
            .uri("/api/dsl/definitions/import?dryRun={dryRun}", dryRun)
            .body(bundle).retrieve().body(ImportBundleResult.class);
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

  private static URI fileUri(UriBuilder uriBuilder, String path) {
    return uriBuilder.path("/api/dsl/files/").pathSegment(path.split("/")).build();
  }

  private <T> T execute(Callable<T> call) {
    try {
      return submit(call).toCompletableFuture().get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while waiting for builder response", e);
    } catch (ExecutionException e) {
      throw propagate(e.getCause());
    }
  }

  <T> CompletionStage<T> submit(Callable<T> call) {
    try {
      return queue.submit(() -> runWithBulkhead(() -> runWithBreaker(() -> invoke(call))));
    } catch (BulkheadFullException e) {
      throw new BuilderClientBusyException("builder request queue is full");
    } catch (RejectedExecutionException e) {
      throw new BuilderClientBusyException("builder request queue is full");
    }
  }

  private <T> T runWithBulkhead(Callable<T> call) throws Exception {
    try {
      return bulkhead.executeCallable(call);
    } catch (BulkheadFullException e) {
      throw new IllegalStateException("builder client bulkhead saturated");
    }
  }

  private <T> T runWithBreaker(Callable<T> call) throws Exception {
    try {
      return breaker.executeCallable(call);
    } catch (CallNotPermittedException e) {
      throw new BuilderUnavailableException("DSL builder circuit breaker is open");
    }
  }

  private <T> T invoke(Callable<T> call) throws Exception {
    try {
      return call.call();
    } catch (ResourceAccessException e) {
      throw new BuilderUnavailableException("DSL builder unreachable: " + e.getMessage(), e);
    }
  }

  private RuntimeException propagate(Throwable throwable) {
    if (throwable instanceof RuntimeException runtimeException) {
      return runtimeException;
    }
    if (throwable instanceof Error error) {
      throw error;
    }
    return new IllegalStateException("builder call failed: " + throwable.getMessage(), throwable);
  }

}
