package cbs.nova.starter.service;

import cbs.nova.dsl.GlobalManager;
import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.VcsModels.DefinitionBundle;
import cbs.nova.starter.model.VcsModels.DefinitionBundleEntry;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import cbs.nova.starter.model.VcsModels.ImportEntryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

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
import java.util.Optional;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class DslDefinitionBundleService {

  private static final Pattern DSL_NAME_PATTERN = Pattern.compile(
          "Dsl\\.(process|transaction|function)\\s*\\(\\s*\"([^\"]+)\"");
  private static final String DEFAULT_SOURCE_LABEL = "source";
  private static final String DRAFT_SOURCE_LABEL = "draft";

  private final ObjectMapper objectMapper;
  private final Optional<BuildProperties> buildProperties;
  private final DslProperties dslProperties;
  private final DslSourcePathResolver sourcePathResolver;
  private final ObjectProvider<DslBuilderClient> builderClientProvider;

  /**
   * Exports a bundle from the configured DSL workspace. When the target directory is the current
   * workspace and a builder client is available, content is read at {@code HEAD} by default and
   * from the working tree when {@code includeDrafts=true}. Otherwise content is read directly from
   * the filesystem.
   */
  public DefinitionBundle export(Path dir, boolean includeDrafts) {
    return exportSelected(dir, includeDrafts, null);
  }

  /**
   * Exports selected (or all) definitions from {@code dir}. Names are resolved from the loaded
   * {@link GlobalManager} when {@code dir} matches the current source directory, otherwise by
   * scanning DSL source files and extracting declared names.
   */
  public DefinitionBundle exportSelected(Path dir, boolean includeDrafts, List<String> names) {
    List<DefinitionBundleEntry> entries = currentSourceDir()
            .filter(d -> d.equals(dir.normalize()))
            .map(_ -> exportFromLoadedDefinitions(dir, includeDrafts, names))
            .orElseGet(() -> exportFromDirectory(dir, includeDrafts, names));
    List<DefinitionBundleEntry> sorted = entries.stream()
            .filter(e -> names == null || names.isEmpty()
                    || names.contains(e.definition().name()))
            .sorted(Comparator.comparing(e -> e.definition().name()))
            .toList();
    String digest = computeDigest(sorted);
    return new DefinitionBundle(StarterConstants.BUNDLE_FORMAT_VERSION, engineVersion(),
            Instant.now().toString(), sorted, digest);
  }

  /**
   * Computes a SHA-256 digest over the canonical JSON serialization of the given definition list.
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
   * Classifies each bundle entry against the DSL source files in {@code targetDir}.
   */
  public List<ImportEntryResult> diffForImport(Path targetDir, DefinitionBundle bundle) {
    List<ImportEntryResult> results = new ArrayList<>();
    for (DefinitionBundleEntry entry : bundle.definitions()) {
      if (entry == null || entry.definition() == null
              || entry.definition().name() == null || entry.definition().name().isBlank()) {
        results.add(new ImportEntryResult("?", "skipped", "invalid entry: missing or blank name"));
        continue;
      }
      String name = entry.definition().name();
      Optional<Path> target = resolveTargetPath(targetDir, name);
      if (target.isEmpty()) {
        results.add(
                new ImportEntryResult(name, "skipped", "unknown definition name: no source path"));
        continue;
      }
      String incoming = entry.definition().source();
      try {
        if (!Files.exists(target.get())) {
          results.add(new ImportEntryResult(name, "created", null));
          continue;
        }
        String existing = Files.readString(target.get(), StandardCharsets.UTF_8);
        if (existing.equals(incoming == null ? "" : incoming)) {
          results.add(new ImportEntryResult(name, "unchanged", null));
        } else {
          long delta = (incoming == null ? 0 : incoming.length()) - existing.length();
          results.add(new ImportEntryResult(name, "updated",
                  "definition differs (" + (delta >= 0 ? "+" : "") + delta + " bytes)"));
        }
      } catch (Exception e) {
        results.add(new ImportEntryResult(name, "updated",
                "existing file unreadable, will be overwritten: " + e.getMessage()));
      }
    }
    return results;
  }

  /**
   * Writes the bundle's DSL source files into {@code targetDir}. Per-entry failures are reported as
   * {@code skipped} results and do not abort the remaining entries.
   */
  public List<ImportEntryResult> applyToTarget(Path targetDir, DefinitionBundle bundle) {
    List<ImportEntryResult> results = new ArrayList<>();
    for (DefinitionBundleEntry entry : bundle.definitions()) {
      DraftRequest payload = entry == null ? null : entry.definition();
      if (payload == null || payload.name() == null || payload.name().isBlank()) {
        results.add(new ImportEntryResult("?", "skipped", "invalid entry: missing or blank name"));
        continue;
      }
      String name = payload.name();
      Optional<Path> target = resolveTargetPath(targetDir, name);
      if (target.isEmpty()) {
        results.add(
                new ImportEntryResult(name, "skipped", "unknown definition name: no source path"));
        continue;
      }
      try {
        Path file = target.get();
        Files.createDirectories(file.getParent());
        String content = payload.source() == null ? "" : payload.source();
        Files.writeString(file, content, StandardCharsets.UTF_8);
        results.add(new ImportEntryResult(name, "published", null));
      } catch (Exception e) {
        results.add(new ImportEntryResult(name, "failed", String.valueOf(e.getMessage())));
      }
    }
    return results;
  }

  public void verifyApplied(Path targetDir, DefinitionBundle bundle) {
    DefinitionBundle reexported = exportSelected(targetDir, false,
            bundle.definitions().stream()
                    .map(e -> e.definition().name())
                    .toList());
    String expectedDigest = computeDigest(bundle.definitions());
    String recomputed = computeDigest(reexported.definitions());
    if (!expectedDigest.equals(recomputed)) {
      throw new IllegalArgumentException("BUNDLE_DIGEST_MISMATCH: promoted definitions on target"
              + " do not match bundle digest (expected " + expectedDigest
              + ", recomputed " + recomputed + ")");
    }
  }

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

  private List<DefinitionBundleEntry> exportFromLoadedDefinitions(Path dir, boolean includeDrafts,
          List<String> names) {
    Map<String, DefinitionBundleEntry> entries = new LinkedHashMap<>();
    GlobalManager gm = GlobalManager.globalManager();
    gm.processNames()
            .forEach(name -> addIfSelected(entries, name, "process", dir, includeDrafts, names));
    gm.transactionNames().forEach(
            name -> addIfSelected(entries, name, "transaction", dir, includeDrafts, names));
    gm.helperNames()
            .forEach(name -> addIfSelected(entries, name, "function", dir, includeDrafts, names));
    return List.copyOf(entries.values());
  }

  private void addIfSelected(Map<String, DefinitionBundleEntry> entries, String name, String type,
          Path dir, boolean includeDrafts, List<String> names) {
    if (names != null && !names.isEmpty() && !names.contains(name)) {
      return;
    }
    Optional<String> path = sourcePathResolver.relativePath(name);
    if (path.isEmpty()) {
      return;
    }
    String content = readContent(dir, path.get(), includeDrafts);
    entries.putIfAbsent(name, new DefinitionBundleEntry(
            new DraftRequest(name, type, includeDrafts ? "Draft" : "Published",
                    null, null, content, null),
            includeDrafts ? DRAFT_SOURCE_LABEL : DEFAULT_SOURCE_LABEL));
  }

  private List<DefinitionBundleEntry> exportFromDirectory(Path dir, boolean includeDrafts,
          List<String> names) {
    Map<String, DefinitionBundleEntry> entries = new LinkedHashMap<>();
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> stream = Files.walk(dir)) {
      stream.filter(Files::isRegularFile)
              .filter(p -> p.getFileName().toString().endsWith(".java"))
              .forEach(file -> readFileIntoEntries(file, dir, includeDrafts, entries, names));
    } catch (IOException e) {
      log.warn("[DSL bundle] failed to walk source directory {}: {}", dir, e.getMessage());
    }
    return List.copyOf(entries.values());
  }

  private void readFileIntoEntries(Path file, Path dir, boolean includeDrafts,
          Map<String, DefinitionBundleEntry> entries, List<String> names) {
    try {
      String content = Files.readString(file, StandardCharsets.UTF_8);
      Matcher matcher = DSL_NAME_PATTERN.matcher(content);
      while (matcher.find()) {
        String type = matcher.group(1);
        String name = matcher.group(2);
        if (name == null || name.isBlank()) {
          continue;
        }
        if (names != null && !names.isEmpty() && !names.contains(name)) {
          continue;
        }
        entries.putIfAbsent(name, new DefinitionBundleEntry(
                new DraftRequest(name, type, includeDrafts ? "Draft" : "Published",
                        null, null, content, null),
                includeDrafts ? DRAFT_SOURCE_LABEL : DEFAULT_SOURCE_LABEL));
      }
    } catch (IOException e) {
      log.warn("[DSL bundle] failed to read {}: {}", file, e.getMessage());
    }
  }

  private String readContent(Path dir, String relativePath, boolean includeDrafts) {
    DslBuilderClient builder = builderClientOrNull();
    if (builder != null) {
      try {
        if (includeDrafts) {
          return builder.readFile(relativePath).content();
        }
        return builder.vcsShow(relativePath, "HEAD");
      } catch (Exception e) {
        log.debug("[DSL bundle] builder read failed for {}, falling back to filesystem: {}",
                relativePath, e.getMessage());
      }
    }
    Path file = dir.resolve(relativePath).normalize();
    if (!file.startsWith(dir.normalize()) || !Files.isRegularFile(file)) {
      return null;
    }
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.warn("[DSL bundle] failed to read {}: {}", file, e.getMessage());
      return null;
    }
  }

  private Optional<Path> resolveTargetPath(Path targetDir, String name) {
    return sourcePathResolver.relativePath(name)
            .map(targetDir::resolve)
            .map(Path::normalize)
            .filter(p -> p.startsWith(targetDir.normalize()));
  }

  private Optional<Path> currentSourceDir() {
    String sourceDirProperty = dslProperties.sourceDir();
    if (sourceDirProperty == null || sourceDirProperty.isBlank()) {
      return Optional.empty();
    }
    Path dir = Path.of(sourceDirProperty).normalize();
    return Files.isDirectory(dir) ? Optional.of(dir) : Optional.empty();
  }

  private DslBuilderClient builderClientOrNull() {
    if (builderClientProvider == null) {
      return null;
    }
    return builderClientProvider.getIfAvailable();
  }

  private ObjectMapper canonicalMapper() {
    return JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
  }

  private String engineVersion() {
    return buildProperties.map(BuildProperties::getVersion)
            .orElseGet(() -> {
              String v = getClass().getPackage().getImplementationVersion();
              return v != null ? v : "dev";
            });
  }

}
