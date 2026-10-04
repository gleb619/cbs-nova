package cbs.nova.dsl.builder.service;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.model.FileEntityKind;
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
 * Maps a DSL construct/model/doc {@code name} to a workspace-relative path.
 *
 * <p>
 * Resolution is cached per kind/name and invalidated when the staged file buffer changes, so
 * callers always see the latest unflushed edits.
 */
@Service
public class ByNamePathResolver {

  private static final String DSL_FILE_SUFFIX = "Dsl.java";
  private static final String MODELS_FILE_SUFFIX = "Models.java";
  private static final String JAVA_SUFFIX = ".java";
  private static final String MARKDOWN_SUFFIX = ".md";
  private static final String DSL_DIR = "src/dsl";
  private static final String MODELS_DIR = "src/models";
  private static final List<String> SOURCE_DIRS = List.of(DSL_DIR, MODELS_DIR);
  private static final String TYPE_DECLARATION = "\\b(?:record|class|interface|enum)\\s+%s\\b";

  private final DslBuilderProperties properties;
  private final FileService fileService;
  private final Cache<String, CachedPath> sourcePaths;
  private final Cache<String, CachedPath> modelPaths;
  private final Cache<String, CachedPath> docPaths;

  public ByNamePathResolver(DslBuilderProperties properties, FileService fileService) {
    this.properties = properties;
    this.fileService = fileService;
    Caffeine<Object, Object> builder = pathCacheBuilder(properties.pathCache());
    this.sourcePaths = builder.<String, CachedPath>build();
    this.modelPaths = builder.<String, CachedPath>build();
    this.docPaths = builder.<String, CachedPath>build();
  }

  static Caffeine<Object, Object> pathCacheBuilder(DslBuilderProperties.PathCache pathCache) {
    int maxEntries = Math.max(1, pathCache.maxEntries());
    long ttlSeconds = Math.max(1L, pathCache.expireAfterWriteSeconds());
    return Caffeine.newBuilder()
            .maximumSize(maxEntries)
            .expireAfterWrite(Duration.ofSeconds(ttlSeconds));
  }

  /**
   * Resolves a name to a workspace-relative path for the given kind.
   */
  public Optional<String> resolvePath(String name, FileEntityKind kind) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    return switch (kind) {
      case DSL -> cached(sourcePaths, name, () -> resolveSource(name));
      case MODEL -> cached(modelPaths, name, () -> resolveModel(name));
      case DOC -> cached(docPaths, name, () -> resolveDoc(name));
    };
  }

  /**
   * Legacy DSL/source resolution (searches {@code src/dsl} first, then {@code src/models}).
   */
  public Optional<String> resolveSourcePath(String name) {
    return resolvePath(name, FileEntityKind.DSL);
  }

  /**
   * Model-only resolution (searches {@code src/models}).
   */
  public Optional<String> resolveModelPath(String name) {
    return resolvePath(name, FileEntityKind.MODEL);
  }

  /**
   * Legacy docs resolution: returns a path relative to the docs sub-directory.
   */
  public Optional<String> resolveDocPath(String name) {
    return resolvePath(name, FileEntityKind.DOC)
            .map(path -> {
              String docsSubDir = properties.docs().subDir();
              if (path.startsWith(docsSubDir + "/")) {
                return path.substring(docsSubDir.length() + 1);
              }
              return path;
            });
  }

  /**
   * Default workspace-relative path for a new entity of the given kind.
   */
  public String defaultPath(String name, FileEntityKind kind) {
    return switch (kind) {
      case DSL -> DSL_DIR + "/" + name + DSL_FILE_SUFFIX;
      case MODEL -> MODELS_DIR + "/" + name + MODELS_FILE_SUFFIX;
      case DOC -> properties.docs().subDir() + "/" + Strings.toKebabCase(name) + MARKDOWN_SUFFIX;
    };
  }

  /**
   * Default docs path relative to the docs sub-directory.
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
    return findTypeDeclaration(name, MODELS_DIR);
  }

  private Optional<String> resolveModel(String name) {
    String holderCandidate = MODELS_DIR + "/" + name + MODELS_FILE_SUFFIX;
    if (fileService.exists(holderCandidate)) {
      return Optional.of(holderCandidate);
    }
    String plainCandidate = MODELS_DIR + "/" + name + JAVA_SUFFIX;
    if (fileService.exists(plainCandidate)) {
      return Optional.of(plainCandidate);
    }
    return findTypeDeclaration(name, MODELS_DIR);
  }

  private Optional<String> resolveDoc(String name) {
    String docsSubDir = properties.docs().subDir();
    for (String candidate : List.of(defaultDocPath(name), name + MARKDOWN_SUFFIX)) {
      String workspacePath = docsSubDir + "/" + candidate;
      if (fileService.exists(workspacePath)) {
        return Optional.of(workspacePath);
      }
    }
    return fileService.listAllFiles(FileEntityKind.DOC, null).stream()
            .map(entry -> docsSubDir + "/" + entry.path())
            .filter(path -> name.equals(FrontmatterNameParser.name(readQuietly(path))))
            .findFirst();
  }

  private Optional<String> findTypeDeclaration(String name, String searchDir) {
    Pattern declaration = Pattern.compile(TYPE_DECLARATION.formatted(Pattern.quote(name)));
    return fileService.listAllFiles(FileEntityKind.MODEL, null).stream()
            .map(FileEntry::path)
            .filter(path -> declaresType(path, declaration))
            .findFirst();
  }

  private boolean declaresType(String relativePath, Pattern declaration) {
    String content = readQuietly(relativePath);
    return content != null && declaration.matcher(content).find();
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
