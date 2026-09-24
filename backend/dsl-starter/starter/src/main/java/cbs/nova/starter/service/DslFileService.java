package cbs.nova.starter.service;

import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.model.DslFileModels.FileContentRequest;
import cbs.nova.starter.model.DslFileModels.FileContentResponse;
import cbs.nova.starter.model.DslFileModels.FileEntry;
import cbs.nova.starter.model.DslFileModels.FlushResult;
import cbs.nova.starter.repository.DslFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DslFileService {

  private final DslProperties dslProperties;
  private final DslWorkspaceResolver workspaceResolver;
  private final DslFileRepository repository;
  private final DslFileBuffer buffer;
  private final DslFileBulkhead bulkhead;
  private final ObjectProvider<DslBuilderClient> builderClientProvider;

  public List<FileEntry> listFiles(String prefix) {
    return builderClient().listFiles(prefix);
  }

  public FileContentResponse readFile(String relativePath) {
    return builderClient().readFile(relativePath);
  }

  public boolean exists(String relativePath) {
    return builderClient().fileExists(relativePath);
  }

  public void stageWrite(String relativePath, String content) {
    builderClient().stageWrite(relativePath, content);
  }

  public int stageAll(List<FileContentRequest> files) {
    return builderClient().stageAll(files).staged();
  }

  public FlushResult flushPending() {
    return builderClient().flushFiles();
  }

  public int pendingCount() {
    return builderClient().pendingCount();
  }

  private DslBuilderClient builderClient() {
    DslBuilderClient client = builderClientProvider == null
            ? null
            : builderClientProvider.getIfAvailable();
    if (client == null) {
      throw new IllegalStateException("DslBuilderClient not available");
    }
    return client;
  }
}
