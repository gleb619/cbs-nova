package cbs.nova.dsl.builder.service;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.model.FileEntityKind;
import java.nio.file.Path;
import java.util.List;

/**
 * Workspace layout rules for each {@link FileEntityKind}.
 *
 * <p>
 * Keeps the kind-specific sub-directory, suffix and by-name search directories in one place so
 * {@link FileService}, {@link ByNamePathResolver} and the controllers do not hard-code layout
 * details.
 */
public final class FileEntityProfiles {

  private static final String JAVA_SUFFIX = ".java";
  private static final String MARKDOWN_SUFFIX = ".md";
  private static final String DSL_DIR = "src/dsl";
  private static final String MODELS_DIR = "src/models";
  private static final String DEFAULT_DOCS_SUB_DIR = "docs";

  private FileEntityProfiles() {
  }

  public static Profile of(FileEntityKind kind, DslBuilderProperties properties) {
    String docsSubDir = properties == null ? DEFAULT_DOCS_SUB_DIR : properties.docs().subDir();
    if (docsSubDir == null || docsSubDir.isBlank()) {
      docsSubDir = DEFAULT_DOCS_SUB_DIR;
    }
    return switch (kind) {
      case DSL -> new Profile(null, JAVA_SUFFIX, List.of(DSL_DIR, MODELS_DIR));
      case MODEL -> new Profile(null, JAVA_SUFFIX, List.of(MODELS_DIR));
      case DOC -> new Profile(docsSubDir, MARKDOWN_SUFFIX, List.of(""));
    };
  }

  public record Profile(String rootSubDir, String suffix, List<String> searchDirs) {

    public String rootPrefix() {
      return rootSubDir == null || rootSubDir.isBlank() ? "" : rootSubDir;
    }

    public Path rootPath(Path workspaceRoot) {
      return rootSubDir == null || rootSubDir.isBlank()
              ? workspaceRoot
              : workspaceRoot.resolve(rootSubDir);
    }
  }
}
