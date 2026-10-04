package cbs.nova.dsl.builder.service;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.model.DslFileModels.FileContentRequest;
import cbs.nova.dsl.builder.model.DslFileModels.FileContentResponse;
import cbs.nova.dsl.builder.model.DslFileModels.FileEntry;
import cbs.nova.dsl.builder.model.DslFileModels.FlushResult;
import cbs.nova.dsl.builder.model.FileEntityKind;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DocsService {

  private final DslBuilderProperties properties;
  private final FileService fileService;

  public List<FileEntry> listDocs(String prefix) {
    return fileService.listFiles(FileEntityKind.DOC, prefix);
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
    return fileService.stageAll(files.stream()
            .map(f -> new FileContentRequest(prefixed(f.path()), f.content()))
            .toList());
  }

  public FlushResult flushDocs() {
    return fileService.flushPending();
  }

  public int pendingDocs() {
    return fileService.pendingCount();
  }

  private String prefixed(String relativePath) {
    String docsSubDir = properties.docs().subDir();
    if (relativePath == null || relativePath.isBlank()) {
      return docsSubDir;
    }
    if (relativePath.startsWith(docsSubDir + "/")) {
      return relativePath;
    }
    return docsSubDir + "/" + relativePath;
  }
}
