package cbs.nova.starter.service;

import static cbs.nova.starter.core.StarterConstants.JSON_SUFFIX;
import static cbs.nova.starter.core.StarterConstants.WORKBENCH_DRAFTS_DIR;
import static cbs.nova.starter.core.StarterConstants.WORKBENCH_PUBLISHED_DIR;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.model.VcsModels.DefinitionBundle;
import cbs.nova.starter.model.VcsModels.DefinitionBundleEntry;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import cbs.nova.starter.model.VcsModels.ImportEntryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class DslDefinitionBundleService {

  private final ObjectMapper objectMapper;
  private final Optional<BuildProperties> buildProperties;
  private final DslProperties dslProperties;

  /**
   * Reads the published metadata markers (and optionally drafts) under the given source directory
   * and returns a portable bundle. The bundle carries metadata only — it does NOT contain DSL
   * source code. The corresponding {@code .java} files must be deployed separately.
   */
  public DefinitionBundle export(Path dir, boolean includeDrafts) {
    return exportSelected(dir, includeDrafts, null);
  }

  /**
   * Like {@link #export(Path, boolean)} but restricted to the given definition names. A
   * {@code null} or empty {@code names} list exports everything. The returned bundle carries a
   * freshly computed digest over the selected (sorted) entries, so the digest always matches the
   * exported content.
   */
  public DefinitionBundle exportSelected(Path dir, boolean includeDrafts, List<String> names) {
    Map<String, DefinitionBundleEntry> entries = new LinkedHashMap<>();
    readInto(entries, dir.resolve(WORKBENCH_PUBLISHED_DIR), "published");
    if (includeDrafts) {
      readInto(entries, dir.resolve(WORKBENCH_DRAFTS_DIR), "draft");
    }
    List<DefinitionBundleEntry> sorted = entries.values().stream()
            .filter(e -> names == null || names.isEmpty()
                    || names.contains(e.definition().name()))
            .sorted(Comparator.comparing(e -> e.definition().name()))
            .toList();
    String digest = computeDigest(sorted);
    return new DefinitionBundle(StarterConstants.BUNDLE_FORMAT_VERSION, engineVersion(),
            Instant.now().toString(),
            sorted, digest);
  }

  /**
   * Computes a SHA-256 digest over the canonical JSON serialization of the given definition list.
   * The list is sorted by definition name and serialized with stable map key ordering so the result
   * is reproducible across exports.
   */
  public String computeDigest(List<DefinitionBundleEntry> definitions) {
    List<DefinitionBundleEntry> sorted = (definitions == null
            ? List.<DefinitionBundleEntry>of()
            : definitions).stream()
            .sorted(Comparator.comparing(e -> e.definition() == null ? "" : e.definition().name()))
            .toList();
    try {
      String canonical = canonicalMapper().writeValueAsString(sorted);
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      throw new IllegalArgumentException("BUNDLE_DIGEST_MISMATCH: failed to compute digest: "
              + e.getMessage(), e);
    }
  }

  /**
   * Verifies that a bundle's digest matches its contents. Throws {@link IllegalArgumentException}
   * with code {@code BUNDLE_DIGEST_MISSING} or {@code BUNDLE_DIGEST_MISMATCH} when verification
   * fails; these are mapped to {@code 400 Bad Request} by the shared exception handler.
   */
  public void verifyDigest(DefinitionBundle bundle) {
    String digest = bundle.digest();
    if (digest == null) {
      if (dslProperties.bundles().requireDigest()) {
        throw new IllegalArgumentException(
                "BUNDLE_DIGEST_MISSING: bundle digest is required but not present");
      }
      log.warn("[DSL bundle] importing bundle without digest; skipping verification");
      return;
    }
    String recomputed = computeDigest(bundle.definitions());
    if (!recomputed.equals(digest)) {
      throw new IllegalArgumentException("BUNDLE_DIGEST_MISMATCH: bundle digest does not match "
              + "(expected " + digest + ", recomputed " + recomputed + ")");
    }
  }

  /**
   * Classifies each bundle entry against the published markers already on disk, returning the
   * preview outcome for a dry-run import. Nothing is written.
   */
  public List<ImportEntryResult> diffForImport(Path dir, DefinitionBundle bundle) {
    List<ImportEntryResult> results = new ArrayList<>();
    Path publishedDir = dir.resolve(WORKBENCH_PUBLISHED_DIR);
    for (DefinitionBundleEntry entry : bundle.definitions()) {
      if (entry == null || entry.definition() == null
              || entry.definition().name() == null || entry.definition().name().isBlank()) {
        results.add(new ImportEntryResult("?", "skipped", "invalid entry: missing or blank name"));
        continue;
      }
      DraftRequest incoming = entry.definition();
      String name = incoming.name();
      Path file = publishedDir.resolve(safeFileName(name) + JSON_SUFFIX);
      if (!Files.isRegularFile(file)) {
        results.add(new ImportEntryResult(name, "created", null));
        continue;
      }
      try {
        DraftRequest existing = objectMapper.readValue(file.toFile(), DraftRequest.class);
        String existingJson = canonicalMapper().writeValueAsString(existing);
        String incomingJson = canonicalMapper().writeValueAsString(incoming);
        if (existingJson.equals(incomingJson)) {
          results.add(new ImportEntryResult(name, "unchanged", null));
        } else {
          long delta = incomingJson.length() - existingJson.length();
          results.add(new ImportEntryResult(name, "updated",
                  "definition differs (" + (delta >= 0 ? "+" : "") + delta + " bytes)"));
        }
      } catch (Exception e) {
        results.add(new ImportEntryResult(name, "updated",
                "existing marker unreadable, will be overwritten: " + e.getMessage()));
      }
    }
    return results;
  }

  /**
   * Writes the bundle's published markers into the target environment's
   * {@code .workbench/published} directory, overwriting existing markers with the same name.
   * Per-entry failures are reported as {@code failed} results and do not abort the remaining
   * entries; callers run {@link #verifyApplied} afterwards to prove the target state matches the
   * bundle digest.
   */
  public List<ImportEntryResult> applyToTarget(Path targetDir, DefinitionBundle bundle) {
    List<ImportEntryResult> results = new ArrayList<>();
    Path publishedDir = targetDir.resolve(WORKBENCH_PUBLISHED_DIR);
    for (DefinitionBundleEntry entry : bundle.definitions()) {
      DraftRequest payload = entry == null ? null : entry.definition();
      if (payload == null || payload.name() == null || payload.name().isBlank()) {
        results.add(new ImportEntryResult("?", "skipped", "invalid entry: missing or blank name"));
        continue;
      }
      try {
        DraftRequest published = new DraftRequest(payload.name(), payload.type(), "Published",
                payload.version(), payload.taskQueue(), payload.source(), payload.savedAt());
        Files.createDirectories(publishedDir);
        Path file = publishedDir.resolve(safeFileName(published.name()) + JSON_SUFFIX);
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(published);
        Files.writeString(file, json, StandardCharsets.UTF_8);
        results.add(new ImportEntryResult(published.name(), "published", null));
      } catch (Exception e) {
        results.add(
                new ImportEntryResult(payload.name(), "failed", String.valueOf(e.getMessage())));
      }
    }
    return results;
  }

  /**
   * Re-reads the promoted definitions from the target environment and verifies their digest matches
   * the bundle. The comparison normalizes the {@code source} label to {@code published} on both
   * sides so bundles that included drafts still verify. Throws {@link IllegalArgumentException}
   * with code {@code BUNDLE_DIGEST_MISMATCH} when the target state does not reproduce the bundle
   * content.
   */
  public void verifyApplied(Path targetDir, DefinitionBundle bundle) {
    List<String> names = bundle.definitions().stream()
            .filter(e -> e != null && e.definition() != null && e.definition().name() != null)
            .map(e -> e.definition().name())
            .toList();
    DefinitionBundle reexported = exportSelected(targetDir, false, names);
    List<DefinitionBundleEntry> expected = bundle.definitions().stream()
            .map(e -> new DefinitionBundleEntry(e.definition(), "published"))
            .toList();
    String expectedDigest = computeDigest(expected);
    String recomputed = computeDigest(reexported.definitions());
    if (!expectedDigest.equals(recomputed)) {
      throw new IllegalArgumentException("BUNDLE_DIGEST_MISMATCH: promoted definitions on target"
              + " do not match bundle digest (expected " + expectedDigest
              + ", recomputed " + recomputed + ")");
    }
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
    if (bundle.formatVersion() != StarterConstants.BUNDLE_FORMAT_VERSION) {
      throw new IllegalArgumentException(
              "Unsupported bundle formatVersion " + bundle.formatVersion()
                      + " (expected " + StarterConstants.BUNDLE_FORMAT_VERSION + ")");
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

  private ObjectMapper canonicalMapper() {
    return JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
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

  private static String safeFileName(String name) {
    return name.replaceAll("[^A-Za-z0-9._-]", "_");
  }

  // follow-up: HMAC signature over digest (cbs.dsl.bundles.signing-key) — flagged for T569;
  // skipped deliberately: exceeds the ~30-line stretch budget, no signing-key infra exists yet.
}
