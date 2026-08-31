package cbs.nova.starter.service;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.model.VcsModels.DefinitionHistoryEntry;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class DslDefinitionHistoryService {

  private static final String HISTORY_DIR = ".workbench/history";
  private static final String PUBLISHED_DIR = ".workbench/published";
  private static final String TIMESTAMP_PATTERN = "^[0-9]+$";
  private static final String JSON_SUFFIX = ".json";

  private final DslProperties dslProperties;
  private final ObjectMapper objectMapper;

  public void snapshotBeforePublish(Path dir, String name) {
    try {
      Path published = safePublishedFile(dir, name);
      if (!Files.exists(published)) {
        return;
      }
      Path historyDir = safeHistoryDir(dir, name);
      Files.createDirectories(historyDir);
      Path target = historyDir.resolve(System.currentTimeMillis() + JSON_SUFFIX);
      Files.copy(published, target);
      prune(historyDir);
    } catch (Exception e) {
      log.warn("[DSL drafts] failed to snapshot publish history for {}: {}", name, e.getMessage());
    }
  }

  public List<DefinitionHistoryEntry> list(Path dir, String name) {
    Path historyDir = safeHistoryDir(dir, name);
    if (!Files.isDirectory(historyDir)) {
      return List.of();
    }
    try (Stream<Path> stream = Files.list(historyDir)) {
      return stream
              .filter(Files::isRegularFile)
              .filter(p -> p.getFileName().toString().endsWith(JSON_SUFFIX))
              .map(this::toEntry)
              .filter(Optional::isPresent)
              .map(Optional::get)
              .sorted(Comparator.comparingLong(DefinitionHistoryEntry::timestampMillis).reversed())
              .toList();
    } catch (Exception e) {
      log.warn("[DSL drafts] failed to list publish history for {}: {}", name, e.getMessage());
      return List.of();
    }
  }

  public Optional<DraftRequest> readEntry(Path dir, String name, String timestamp) {
    if (timestamp == null || !timestamp.matches(TIMESTAMP_PATTERN)) {
      return Optional.empty();
    }
    Path file = safeHistoryFile(dir, name, timestamp);
    if (!Files.exists(file)) {
      return Optional.empty();
    }
    try {
      return Optional.of(objectMapper.readValue(file.toFile(), DraftRequest.class));
    } catch (Exception e) {
      log.warn("[DSL drafts] failed to read publish history entry {} for {}: {}",
              timestamp, name, e.getMessage());
      return Optional.empty();
    }
  }

  private void prune(Path historyDir) throws IOException {
    int limit = dslProperties.getDrafts().getHistoryLimit();
    if (limit <= 0) {
      return;
    }
    List<Path> files;
    try (Stream<Path> stream = Files.list(historyDir)) {
      files = stream
              .filter(Files::isRegularFile)
              .filter(p -> p.getFileName().toString().endsWith(JSON_SUFFIX))
              .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
              .toList();
    }
    for (int i = limit; i < files.size(); i++) {
      Files.deleteIfExists(files.get(i));
    }
  }

  private Optional<DefinitionHistoryEntry> toEntry(Path file) {
    String name = file.getFileName().toString();
    String timestamp = name.substring(0, name.length() - JSON_SUFFIX.length());
    try {
      long millis = Long.parseLong(timestamp);
      long size = Files.size(file);
      long modified = Files.getLastModifiedTime(file).toMillis();
      return Optional.of(new DefinitionHistoryEntry(timestamp, millis, size, modified));
    } catch (IOException | NumberFormatException e) {
      log.warn("[DSL drafts] skipping malformed history entry {}: {}", file, e.getMessage());
      return Optional.empty();
    }
  }

  private Path safePublishedFile(Path dir, String name) {
    Path publishedDir = dir.resolve(PUBLISHED_DIR).normalize();
    return publishedDir.resolve(safeFileName(name) + JSON_SUFFIX).normalize();
  }

  private Path safeHistoryDir(Path dir, String name) {
    Path historyRoot = dir.resolve(HISTORY_DIR).normalize();
    Path historyDir = historyRoot.resolve(safeFileName(name)).normalize();
    if (!historyDir.startsWith(historyRoot)) {
      throw new IllegalArgumentException("History path escapes root: " + historyDir);
    }
    return historyDir;
  }

  private Path safeHistoryFile(Path dir, String name, String timestamp) {
    Path historyDir = safeHistoryDir(dir, name);
    return historyDir.resolve(timestamp + JSON_SUFFIX).normalize();
  }

  private static String safeFileName(String name) {
    return name.replaceAll("[^A-Za-z0-9._-]", "_");
  }

}
