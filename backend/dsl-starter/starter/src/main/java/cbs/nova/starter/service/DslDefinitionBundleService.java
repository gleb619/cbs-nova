package cbs.nova.starter.service;

import cbs.nova.starter.model.VcsModels.DefinitionBundle;
import cbs.nova.starter.model.VcsModels.DefinitionBundleEntry;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class DslDefinitionBundleService {

  public static final int BUNDLE_FORMAT_VERSION = 1;
  private static final String PUBLISHED_DIR = ".workbench/published";
  private static final String DRAFTS_DIR = ".workbench/drafts";
  private static final String JSON_SUFFIX = ".json";

  private final ObjectMapper objectMapper;
  private final Optional<BuildProperties> buildProperties;

  /**
   * Reads the published metadata markers (and optionally drafts) under the given source directory
   * and returns a portable bundle. The bundle carries metadata only — it does NOT contain DSL
   * source code. The corresponding {@code .java} files must be deployed separately.
   */
  public DefinitionBundle export(Path dir, boolean includeDrafts) {
    Map<String, DefinitionBundleEntry> entries = new LinkedHashMap<>();
    readInto(entries, dir.resolve(PUBLISHED_DIR), "published");
    if (includeDrafts) {
      readInto(entries, dir.resolve(DRAFTS_DIR), "draft");
    }
    List<DefinitionBundleEntry> sorted = entries.values().stream()
            .sorted(Comparator.comparing(e -> e.definition().name()))
            .toList();
    return new DefinitionBundle(BUNDLE_FORMAT_VERSION, engineVersion(), Instant.now().toString(),
            sorted);
  }

  /**
   * Validates that a bundle can be imported by this engine version. Throws
   * {@link IllegalArgumentException} for any structural or format mismatch; these are mapped to
   * {@code 400 Bad Request} by the shared exception handler.
   */
  public void validateForImport(DefinitionBundle bundle) {
    if (bundle == null || bundle.formatVersion() == 0) {
      throw new IllegalArgumentException("bundle: missing or invalid formatVersion");
    }
    if (bundle.formatVersion() != BUNDLE_FORMAT_VERSION) {
      throw new IllegalArgumentException(
              "Unsupported bundle formatVersion " + bundle.formatVersion()
                      + " (expected " + BUNDLE_FORMAT_VERSION + ")");
    }
    if (bundle.definitions() == null || bundle.definitions().isEmpty()) {
      throw new IllegalArgumentException("bundle: no definitions");
    }
    for (DefinitionBundleEntry entry : bundle.definitions()) {
      if (entry == null || entry.definition() == null
              || entry.definition().name() == null || entry.definition().name().isBlank()) {
        throw new IllegalArgumentException("bundle: every entry must have a non-blank name");
      }
    }
  }

  private void readInto(Map<String, DefinitionBundleEntry> target, Path directory, String source) {
    if (!Files.isDirectory(directory)) {
      return;
    }
    try (Stream<Path> stream = Files.list(directory)) {
      List<Path> files = stream
              .filter(Files::isRegularFile)
              .filter(p -> p.getFileName().toString().endsWith(JSON_SUFFIX))
              .toList();
      for (Path file : files) {
        try {
          DraftRequest draft = objectMapper.readValue(file.toFile(), DraftRequest.class);
          if (draft.name() == null || draft.name().isBlank()) {
            log.warn("[DSL bundle] skipping {} because it has no name", file);
            continue;
          }
          target.putIfAbsent(draft.name(), new DefinitionBundleEntry(draft, source));
        } catch (Exception e) {
          log.warn("[DSL bundle] skipping unparseable file {}: {}", file, e.getMessage());
        }
      }
    } catch (Exception e) {
      log.warn("[DSL bundle] failed to list bundle files in {}: {}", directory, e.getMessage());
    }
  }

  private String engineVersion() {
    return buildProperties.map(BuildProperties::getVersion)
            .orElseGet(() -> {
              String v = getClass().getPackage().getImplementationVersion();
              return v != null ? v : "dev";
            });
  }

}
