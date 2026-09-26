package cbs.nova.starter.service;

import cbs.nova.starter.builder.DslBuilderDocsClient;
import cbs.nova.starter.model.DslFileModels.FileContentRequest;
import cbs.nova.starter.model.DslFileModels.FileContentResponse;
import cbs.nova.starter.model.DslFileModels.FileEntry;
import cbs.nova.starter.model.DslFileModels.FlushResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DslDocsService {

  private final ObjectProvider<DslBuilderDocsClient> builderClientProvider;

  public List<FileEntry> listDocs(String prefix) {
    return builderClient().listDocs(prefix);
  }

  public FileContentResponse readDoc(String relativePath) {
    return builderClient().readDoc(relativePath);
  }

  public boolean docExists(String relativePath) {
    return builderClient().docExists(relativePath);
  }

  public void stageDocWrite(String relativePath, String content) {
    builderClient().stageDocWrite(relativePath, content);
  }

  public int stageDocAll(List<FileContentRequest> files) {
    return builderClient().stageDocAll(files).staged();
  }

  public FlushResult flushDocs() {
    return builderClient().flushDocs();
  }

  public int pendingDocs() {
    return builderClient().docsPendingCount();
  }

  private DslBuilderDocsClient builderClient() {
    DslBuilderDocsClient client = builderClientProvider == null
            ? null
            : builderClientProvider.getIfAvailable();
    if (client == null) {
      throw new IllegalStateException("DslBuilderDocsClient not available");
    }
    return client;
  }
}
