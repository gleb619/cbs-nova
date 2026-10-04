package cbs.nova.starter.service;

import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.model.DslFileModels.FileContentRequest;
import cbs.nova.starter.model.DslFileModels.FileContentResponse;
import cbs.nova.starter.model.DslFileModels.FileEntry;
import cbs.nova.starter.model.DslFileModels.FlushResult;
import cbs.nova.starter.model.FileEntityKind;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DslFileService {

  private final DslProperties dslProperties;
  private final DslBuilderClient builderClient;

  public List<FileEntry> listFiles(String prefix) {
    return listFiles(prefix, FileEntityKind.DSL);
  }

  public List<FileEntry> listFiles(String prefix, FileEntityKind kind) {
    return builderClient.listFiles(prefix, kind);
  }

  public FileContentResponse readFile(String relativePath) {
    return readFile(relativePath, FileEntityKind.DSL);
  }

  public FileContentResponse readFile(String relativePath, FileEntityKind kind) {
    return builderClient.readFile(relativePath, kind);
  }

  public FileContentResponse readFileByName(String name) {
    return readFileByName(name, FileEntityKind.DSL);
  }

  public FileContentResponse readFileByName(String name, FileEntityKind kind) {
    return builderClient.readFileByName(name, kind);
  }

  public boolean exists(String relativePath) {
    return exists(relativePath, FileEntityKind.DSL);
  }

  public boolean exists(String relativePath, FileEntityKind kind) {
    return builderClient.fileExists(relativePath, kind);
  }

  public void stageWrite(String relativePath, String content) {
    stageWrite(relativePath, content, FileEntityKind.DSL);
  }

  public void stageWrite(String relativePath, String content, FileEntityKind kind) {
    builderClient.stageWrite(relativePath, content, kind);
  }

  public FileContentResponse stageWriteByName(String name, String content) {
    return stageWriteByName(name, content, FileEntityKind.DSL);
  }

  public FileContentResponse stageWriteByName(String name, String content, FileEntityKind kind) {
    return builderClient.stageWriteByName(name, content, kind);
  }

  public int stageAll(List<FileContentRequest> files) {
    return stageAll(files, FileEntityKind.DSL);
  }

  public int stageAll(List<FileContentRequest> files, FileEntityKind kind) {
    return builderClient.stageAll(files, kind).staged();
  }

  public FlushResult flushPending() {
    return flushPending(FileEntityKind.DSL);
  }

  public FlushResult flushPending(FileEntityKind kind) {
    return builderClient.flushFiles(kind);
  }

  public int pendingCount() {
    return pendingCount(FileEntityKind.DSL);
  }

  public int pendingCount(FileEntityKind kind) {
    return builderClient.pendingCount(kind);
  }
}
