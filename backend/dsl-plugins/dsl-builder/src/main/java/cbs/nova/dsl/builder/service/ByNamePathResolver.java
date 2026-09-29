package cbs.nova.dsl.builder.service;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.model.DslFileModels.FileEntry;
import cbs.nova.dsl.utils.Strings;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Maps a DSL construct {@code name} (the Workbench key, e.g. {@code "BatchProcessing"}) to a path
 * inside this builder's own work tree, so by-name endpoints never depend on the caller's filesystem
 * view of the workspace.
 *
 * <p>
 * The workspace layout is fixed: DSL sources live in {@code src/dsl} and {@code src/models}, docs
 * markdown lives flat under the docs sub-directory ({@code properties.docs().subDir()}). Resolution
 * tries the naming conventions first (first hit wins), then falls back to content scans: a type
 * declaration in any {@code src/models} file for sources, a yaml frontmatter {@code name:} field in
 * any docs file for docs.
 *
 * <p>
 * Reads and existence checks go through {@link FileService} so buffer-staged (not yet flushed)
 * content is respected. Scan results are cached per name in bounded Caffeine caches
 * ({@code sourcePaths}, {@code docPaths}); each entry carries its buffer version, so a stage or
 * flush forces the next resolve to re-scan.
 */
@Service
public class ByNamePathResolver {

  private static final String DSL_FILE_SUFFIX = "Dsl.java";
  private static final String JAVA_SUFFIX = ".java";
  private static final String MARKDOWN_SUFFIX = ".md";
  private static final String MODELS_DIR = "src/models";
  private static final List<String> SOURCE_DIRS = List.of("src/dsl", MODELS_DIR);
  private static final String TYPE_DECLARATION = "\\b(?:record|class|interface|enum)\\s+%s\\b";

  private final DslBuilderProperties properties;
  private final FileService fileService;
  private final Cache<String, CachedPath> sourcePaths;
  private final Cache<String, CachedPath> docPaths;

  public ByNamePathResolver(DslBuilderProperties properties, FileService fileService) {
    this.properties = properties;
    this.fileService = fileService;
    Caffeine<Object, Object> builder = pathCacheBuilder(properties.pathCache());
    this.sourcePaths = builder.<String, CachedPath>build();
    this.docPaths = builder.<String, CachedPath>build();
  }

  /**
   * Visible for testing: rebuild the caches with knobs supplied via the pathCache config so a test
   * can drive a fake ticker or shrink the bound to inspect eviction.
   */
  static Caffeine<Object, Object> pathCacheBuilder(DslBuilderProperties.PathCache pathCache) {
    int maxEntries = Math.max(1, pathCache.maxEntries());
    long ttlSeconds = Math.max(1L, pathCache.expireAfterWriteSeconds());
    return Caffeine.newBuilder()
            .maximumSize(maxEntries)
            .expireAfterWrite(Duration.ofSeconds(ttlSeconds));
  }

  /**
   * @param name
   *          construct name.
   * @return work-tree-relative forward-slash path of the DSL source file, or empty when no
   *         candidate exists.
   */
  public Optional<String> resolveSourcePath(String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    return cached(sourcePaths, name, () -> resolveSource(name));
  }

  /**
   * @param name
   *          construct name.
   * @return docs-root-relative forward-slash path of the existing doc, or empty when no candidate
   *         exists.
   */
  public Optional<String> resolveDocPath(String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    return cached(docPaths, name, () -> resolveDoc(name));
  }

  /**
   * Docs-root-relative path a brand-new doc for {@code name} is written to: the kebab-case
   * convention path.
   */
  public String defaultDocPath(String name) {
    return Strings.toKebabCase(name) + MARKDOWN_SUFFIX;
  }

  private Optional<String> resolveSource(String name) {
    for (String dir : SOURCE_DIRS) {
      for (String filename : List.of(name + DSL_FILE_SUFFIX, name + JAVA_SUFFIX)) {
        String candidate = dir + "/" + filename;
        if (fileService.exists(candidate)) {
          return Optional.of(candidate);
        }
      }
    }
    return findTypeDeclaration(name);
  }

  private Optional<String> findTypeDeclaration(String name) {
    Pattern declaration = Pattern.compile(TYPE_DECLARATION.formatted(Pattern.quote(name)));
    return fileService.listAllFiles(MODELS_DIR, JAVA_SUFFIX).stream()
            .map(FileEntry::path)
            .filter(path -> declaresType(path, declaration))
            .findFirst();
  }

  private boolean declaresType(String relativePath, Pattern declaration) {
    String content = readQuietly(relativePath);
    return content != null && declaration.matcher(content).find();
  }

  private Optional<String> resolveDoc(String name) {
    String docsSubDir = properties.docs().subDir();
    for (String candidate : List.of(defaultDocPath(name), name + MARKDOWN_SUFFIX)) {
      if (fileService.exists(docsSubDir + "/" + candidate)) {
        return Optional.of(candidate);
      }
    }
    return fileService.listAllFiles(docsSubDir, MARKDOWN_SUFFIX).stream()
            .map(FileEntry::path)
            .filter(path -> name.equals(FrontmatterNameParser.name(readQuietly(path))))
            .map(path -> path.substring(docsSubDir.length() + 1))
            .findFirst();
  }

  private String readQuietly(String relativePath) {
    try {
      return fileService.readFile(relativePath).content();
    } catch (IOException e) {
      return null;
    }
  }

  private Optional<String> cached(Cache<String, CachedPath> cache, String name,
          Supplier<Optional<String>> resolution) {
    long bufferVersion = fileService.bufferVersion();
    CachedPath cached = cache.getIfPresent(name);
    if (cached != null && cached.bufferVersion() == bufferVersion) {
      return cached.path();
    }
    Optional<String> path = resolution.get();
    cache.put(name, new CachedPath(bufferVersion, path));
    return path;
  }

  private record CachedPath(long bufferVersion, Optional<String> path) {
  }
}
