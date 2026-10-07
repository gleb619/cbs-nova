package cbs.nova.starter.service;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.starter.entity.DslDefinitionSnapshotEntity;
import cbs.nova.starter.model.DefinitionSnapshotDto;
import cbs.nova.starter.model.DefinitionVersionDto;
import cbs.nova.starter.persistence.DslDefinitionSnapshotCrudRepository;
import cbs.nova.starter.util.UnifiedDiff;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Persists and serves definition source snapshots (T875). Every reload/publish captures the
 * canonical source snapshot once per definition: the same {@code path\ncontent\n…} canonicalization
 * {@link DefinitionContentHash} hashes, so a stored {@code content_hash} resolves back to the exact
 * source a run executed. Capture is idempotent on {@code (definition_name, content_hash)} and never
 * fails the reload/publish that triggered it — errors are logged and swallowed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefinitionSnapshotService {

  /** Source snapshots larger than this are truncated (with a marker) before storage. */
  static final int MAX_SOURCE_TEXT_BYTES = 512 * 1024;

  private final DslDefinitionSnapshotCrudRepository snapshots;

  /**
   * Captures the snapshot for every process/transaction in {@code registry} unless an identical
   * {@code (name, content_hash)} row already exists. {@code capturedBy} is {@code "reload"} or
   * {@code "publish"}.
   */
  public void capture(@Nullable Map<String, String> sources, @Nullable GlobalManager registry,
          String capturedBy) {
    try {
      if (sources == null || sources.isEmpty() || registry == null) {
        log.debug("[DSL snapshots] skip capture: sources={}, registry={}",
                sources == null ? "null" : "empty", registry == null ? "null" : "present");
        return;
      }
      String canonical = DefinitionContentHash.canonicalSnapshotText(sources);
      // Hash the FULL canonical text so the row resolves to dsl_runs.content_hash; the stored
      // source_text alone is size-capped (a truncated row no longer re-hashes to the hash — the
      // truncation marker makes that explicit).
      String hash = DefinitionContentHash.snapshotHashOf(canonical);
      String sourceText = cap(canonical);
      List<String> names = new ArrayList<>(registry.processNames());
      names.addAll(registry.transactionNames());
      int inserted = 0;
      for (String name : names) {
        Optional<DslDescriptor> descriptor = registry.describeProcess(name)
                .or(() -> registry.describeTransaction(name));
        if (descriptor.isEmpty()) {
          continue;
        }
        if (snapshots.countByDefinitionNameAndContentHash(name, hash) > 0) {
          continue;
        }
        snapshots.save(new DslDefinitionSnapshotEntity(null, name,
                descriptor.get().version(), hash, sourceText, Instant.now(), capturedBy));
        inserted++;
      }
      log.info("[DSL snapshots] captured {} new source snapshot(s) for {} definition(s)"
              + " (hash={})", inserted, names.size(), hash);
    } catch (Exception ex) {
      log.warn("[DSL snapshots] failed to capture source snapshot: {}", ex.getMessage(), ex);
    }
  }

  /** Versions of {@code definitionName}, newest capture first. */
  public List<DefinitionVersionDto> versions(String definitionName) {
    return snapshots.findByDefinitionName(definitionName).stream()
            .map(row -> new DefinitionVersionDto(row.contentHash(), row.definitionVersion(),
                    row.capturedAt().toString(), row.capturedBy()))
            .toList();
  }

  /**
   * The stored snapshot for {@code (definitionName, contentHash)} with a unified diff against the
   * previous snapshot of the same definition. {@code diff} is {@code null} for the first snapshot.
   */
  public Optional<DefinitionSnapshotDto> find(String definitionName, String contentHash) {
    List<DslDefinitionSnapshotEntity> matches = snapshots
            .findByDefinitionNameAndContentHash(definitionName, contentHash);
    if (matches.isEmpty()) {
      return Optional.empty();
    }
    DslDefinitionSnapshotEntity row = matches.get(0);
    String diff = previousOf(definitionName, row)
            .map(prev -> UnifiedDiff.diff(prev.sourceText(), row.sourceText()))
            .orElse(null);
    return Optional.of(new DefinitionSnapshotDto(row.definitionName(), row.contentHash(),
            row.definitionVersion(), row.capturedAt().toString(), row.capturedBy(),
            row.sourceText(), diff));
  }

  private Optional<DslDefinitionSnapshotEntity> previousOf(String definitionName,
          DslDefinitionSnapshotEntity row) {
    return snapshots.findByDefinitionName(definitionName).stream()
            .filter(other -> !other.id().equals(row.id()))
            .filter(other -> other.capturedAt().isBefore(row.capturedAt())
                    || other.capturedAt().equals(row.capturedAt())
                            && other.id() < row.id())
            .findFirst();
  }

  private static String cap(String canonical) {
    byte[] bytes = canonical.getBytes(StandardCharsets.UTF_8);
    if (bytes.length <= MAX_SOURCE_TEXT_BYTES) {
      return canonical;
    }
    String marker = "\n…[source truncated: " + (bytes.length - MAX_SOURCE_TEXT_BYTES)
            + " bytes omitted]\n";
    return new String(bytes, 0, MAX_SOURCE_TEXT_BYTES, StandardCharsets.UTF_8) + marker;
  }
}
