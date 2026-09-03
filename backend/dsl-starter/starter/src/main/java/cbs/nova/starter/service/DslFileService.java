package cbs.nova.starter.service;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.model.DslFileModels.FileContentRequest;
import cbs.nova.starter.model.DslFileModels.FileContentResponse;
import cbs.nova.starter.model.DslFileModels.FileEntry;
import cbs.nova.starter.model.DslFileModels.FlushResult;
import cbs.nova.starter.repository.DslFileRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class DslFileService {

  private final DslProperties dslProperties;
  private final DslWorkspaceResolver workspaceResolver;
  private final DslFileRepository repository;
  private final DslFileBuffer buffer;
  private final DslFileBulkhead bulkhead;

  //TODO: since app will be started in docker, we need some other way of lock, to prevent multi flush
  private final ReentrantLock flushLock = new ReentrantLock();
  private ScheduledExecutorService flushExecutor;

  @PostConstruct
  public void start() {
    int interval = dslProperties.getFiles().getFlushIntervalSeconds();
    if (interval > 0) {
      flushExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "dsl-file-flush");
        thread.setDaemon(true);
        return thread;
      });
      flushExecutor.scheduleWithFixedDelay(this::flushPending, interval, interval,
              TimeUnit.SECONDS);
    }
  }

  @PreDestroy
  public void stop() {
    if (flushExecutor != null) {
      flushExecutor.shutdown();
      try {
        if (!flushExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
          flushExecutor.shutdownNow();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        flushExecutor.shutdownNow();
      }
    }
    flushPending();
  }

  public List<FileEntry> listFiles(String prefix) {
    ensureRoot();
    return repository.list(workspaceRoot(), prefix);
  }

  public FileContentResponse readFile(String relativePath) throws IOException {
    ensureRoot();
    String staged = buffer.get(relativePath);
    if (staged != null) {
      return new FileContentResponse(relativePath, staged, true);
    }
    bulkhead.acquireRead();
    try {
      Path workspace = workspaceRoot();
      if (repository.exists(workspace, relativePath)) {
        String content = repository.read(workspace, relativePath);
        return new FileContentResponse(relativePath, content, false);
      }
      Path source = sourceRoot();
      if (repository.exists(source, relativePath)) {
        String content = repository.read(source, relativePath);
        return new FileContentResponse(relativePath, content, false);
      }
      throw new IOException("file not found: " + relativePath);
    } finally {
      bulkhead.releaseRead();
    }
  }

  public boolean exists(String relativePath) {
    ensureRoot();
    if (buffer.get(relativePath) != null) {
      return true;
    }
    return repository.exists(workspaceRoot(), relativePath)
            || repository.exists(sourceRoot(), relativePath);
  }

  public void stageWrite(String relativePath, String content) {
    ensureRoot();
    buffer.stage(relativePath, content);
    if (buffer.pendingCount() >= dslProperties.getFiles().getMaxQueueSize()) {
      if (flushExecutor != null) {
        flushExecutor.execute(this::flushPending);
      }
    }
  }

  public int stageAll(List<FileContentRequest> files) {
    ensureRoot();
    int staged = 0;
    for (FileContentRequest file : files) {
      if (file.path() == null || file.path().isBlank()) {
        continue;
      }
      buffer.stage(file.path(), file.content() == null ? "" : file.content());
      staged++;
    }
    if (buffer.pendingCount() >= dslProperties.getFiles().getMaxQueueSize()) {
      if (flushExecutor != null) {
        flushExecutor.execute(this::flushPending);
      }
    }
    return staged;
  }

  public FlushResult flushPending() {
    ensureRoot();
    if (!flushLock.tryLock()) {
      return new FlushResult(0, 0, List.of("flush already in progress"));
    }
    try {
      Map<String, String> snapshot = buffer.drain();
      if (snapshot.isEmpty()) {
        return new FlushResult(0, 0, List.of());
      }
      Path root = workspaceRoot();
      int flushed = 0;
      int failed = 0;
      List<String> errors = new ArrayList<>();
      for (Map.Entry<String, String> entry : snapshot.entrySet()) {
        bulkhead.acquireWrite();
        try {
          repository.write(root, entry.getKey(), entry.getValue());
          flushed++;
        } catch (Exception e) {
          failed++;
          errors.add(entry.getKey() + ": " + e.getMessage());
          log.warn("[DSL files] failed to flush {}: {}", entry.getKey(), e.getMessage());
        } finally {
          bulkhead.releaseWrite();
        }
      }
      log.info("[DSL files] flushed {} files, {} failed", flushed, failed);
      return new FlushResult(flushed, failed, errors);
    } finally {
      flushLock.unlock();
    }
  }

  public int pendingCount() {
    return buffer.pendingCount();
  }

  private Path sourceRoot() {
    return workspaceResolver.sourceRoot();
  }

  private Path workspaceRoot() {
    return workspaceResolver.workspaceRoot();
  }

  private void ensureRoot() {
    Path root = workspaceRoot();
    if (!Files.isDirectory(root)) {
      try {
        Files.createDirectories(root);
      } catch (IOException e) {
        throw new IllegalStateException("cannot create workspace root: " + root, e);
      }
    }
  }
}
