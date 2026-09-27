package cbs.nova.starter.builder;

import cbs.nova.starter.model.DslFileModels.BulkWriteRequest;
import cbs.nova.starter.model.DslFileModels.BulkWriteResult;
import cbs.nova.starter.model.DslFileModels.FileContentRequest;
import cbs.nova.starter.model.DslFileModels.FileContentResponse;
import cbs.nova.starter.model.DslFileModels.FileEntry;
import cbs.nova.starter.model.DslFileModels.FlushResult;
import cbs.nova.starter.model.DslFileModels.PendingWritesStatus;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Callable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

public class DslBuilderDocsClient {

  private static final ParameterizedTypeReference<List<FileEntry>> FILE_ENTRIES_TYPE = new ParameterizedTypeReference<>() {
  };

  private final RestClient restClient;
  private final BuilderRequestExecutor executor;
  private final BuilderCache cache;

  public DslBuilderDocsClient(RestClient restClient, BuilderRequestExecutor executor,
          BuilderCache cache) {
    this.restClient = restClient;
    this.executor = executor;
    this.cache = cache;
  }

  public List<FileEntry> listDocs(String prefix) {
    return cache.listDocs(prefix, () -> execute(() -> doListDocs(prefix)));
  }

  public FileContentResponse readDoc(String path) {
    return cache.readDoc(path, () -> execute(() -> doReadDoc(path)));
  }

  public boolean docExists(String path) {
    return cache.docExists(path, () -> execute(() -> doDocExists(path)));
  }

  public void stageDocWrite(String path, String content) {
    execute(() -> doStageDocWrite(path, content));
    cache.invalidateDoc(path);
    cache.invalidateVcsStatus();
  }

  public BulkWriteResult stageDocAll(List<FileContentRequest> files) {
    BulkWriteResult result = execute(() -> doStageDocAll(files));
    cache.invalidateDocs();
    cache.invalidateVcsStatus();
    return result;
  }

  public FlushResult flushDocs() {
    FlushResult result = execute(this::doFlushDocs);
    cache.invalidateDocs();
    cache.invalidateVcsStatus();
    return result;
  }

  public int docsPendingCount() {
    return execute(this::doDocsPendingCount);
  }

  private List<FileEntry> doListDocs(String prefix) {
    return restClient.get().uri(uriBuilder -> {
      var builder = uriBuilder.path("/api/dsl/docs");
      return prefix == null || prefix.isBlank()
              ? builder.build()
              : builder.queryParam("prefix", prefix).build();
    }).retrieve().body(FILE_ENTRIES_TYPE);
  }

  private FileContentResponse doReadDoc(String path) {
    return restClient.get().uri(uriBuilder -> docUri(uriBuilder, path)).retrieve()
            .body(FileContentResponse.class);
  }

  private boolean doDocExists(String path) {
    Boolean exists = restClient.get()
            .uri(uriBuilder -> uriBuilder.path("/api/dsl/docs/exists/")
                    .pathSegment(path.split("/")).build())
            .retrieve().body(Boolean.class);
    return Boolean.TRUE.equals(exists);
  }

  private Void doStageDocWrite(String path, String content) {
    return restClient.post().uri(uriBuilder -> docUri(uriBuilder, path)).body(content).retrieve()
            .body(Void.class);
  }

  private BulkWriteResult doStageDocAll(List<FileContentRequest> files) {
    return restClient.post().uri("/api/dsl/docs/bulk")
            .body(new BulkWriteRequest(files)).retrieve().body(BulkWriteResult.class);
  }

  private FlushResult doFlushDocs() {
    return restClient.post().uri("/api/dsl/docs/flush").retrieve().body(FlushResult.class);
  }

  private Integer doDocsPendingCount() {
    var status = restClient.get().uri("/api/dsl/docs/status").retrieve()
            .body(PendingWritesStatus.class);
    return status == null ? 0 : status.pending();
  }

  private static URI docUri(UriBuilder uriBuilder, String path) {
    return uriBuilder.path("/api/dsl/docs/").pathSegment(path.split("/")).build();
  }

  private <T> T execute(Callable<T> call) {
    return executor.execute(call);
  }
}
