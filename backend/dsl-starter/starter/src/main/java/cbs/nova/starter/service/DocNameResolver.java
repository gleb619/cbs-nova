package cbs.nova.starter.service;

import cbs.nova.dsl.utils.Strings;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Maps a DSL construct {@code name} (the Workbench key, e.g. {@code "BatchProcessing"}) to the
 * docs-relative markdown path, mirroring the explain-resource convention: kebab-case first
 * ({@code batch-processing.md}), falling back to the raw {@code name + ".md"}.
 */
@Component
@RequiredArgsConstructor
public class DocNameResolver {

  private static final String MARKDOWN_SUFFIX = ".md";

  private final DslDocsService docsService;

  public Optional<String> existingDocPath(String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    String kebab = kebabPath(name);
    if (docsService.docExists(kebab)) {
      return Optional.of(kebab);
    }
    String raw = name + MARKDOWN_SUFFIX;
    if (docsService.docExists(raw)) {
      return Optional.of(raw);
    }
    return Optional.empty();
  }

  public String defaultDocPath(String name) {
    return kebabPath(name);
  }

  private String kebabPath(String name) {
    return Strings.toKebabCase(name) + MARKDOWN_SUFFIX;
  }
}
