package cbs.nova.dsl.builder.service;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.model.DslFileModels.FileContentRequest;
import cbs.nova.dsl.builder.model.DslFileModels.FileContentResponse;
import cbs.nova.dsl.builder.model.DslFileModels.FileEntry;
import cbs.nova.dsl.builder.model.DslFileModels.FlushResult;
import cbs.nova.dsl.builder.repository.FileRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DocsService {

  private static final String MARKDOWN_SUFFIX = ".md";

  private final DslBuilderProperties properties;
  private final FileService fileService;
  private final FileRepository repository;

  public List<FileEntry> listDocs(String prefix) {
    return repository.list(docsRoot(), prefix, MARKDOWN_SUFFIX);
  }

  public FileContentResponse readDoc(String relativePath) throws IOException {
    return fileService.readFile(prefixed(relativePath));
  }

  public boolean docExists(String relativePath) {
    return fileService.exists(prefixed(relativePath));
  }

  public void stageDocWrite(String relativePath, String content) {
    fileService.stageWrite(prefixed(relativePath), content);
  }

  public int stageDocAll(List<FileContentRequest> files) {
    int staged = 0;
    for (FileContentRequest file : files) {
      if (file.path() == null || file.path().isBlank()) {
        continue;
      }
      stageDocWrite(file.path(), file.content() == null ? "" : file.content());
      staged++;
    }
    return staged;
  }

  public FlushResult flushDocs() {
    return fileService.flushPending();
  }

  public int pendingDocs() {
    return fileService.pendingCount();
  }

  private Path docsRoot() {
    return properties.workspaceDir().resolve(properties.docs().subDir());
  }

  private String prefixed(String relativePath) {
    return properties.docs().subDir() + "/" + relativePath;
  }
}
