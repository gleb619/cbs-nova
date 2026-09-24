package cbs.nova.starter.service;

import cbs.nova.dsl.GlobalManager;
import cbs.nova.starter.config.properties.DslProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Maps a definition {@code name} (the Workbench key, e.g. {@code "LoanDisbursement"}) to the
 * source-relative path of the DSL Java file that holds it, e.g.
 * {@code "dsl/LoanDisbursementDsl.java"}.
 *
 * <p>
 * Used by {@link DslFileHandler} ({@code /api/dsl/files/by-name/...}) and
 * {@link DslDefinitionStatusResolver} (resolve status from the DSL source file's git state, per
 * drafts spec §3.2 / §7 step 2). The lookup is intentionally cheap and cache-free; the
 * {@code resolveAll} caller resolves each name once per call.
 *
 * <p>
 * Resolution steps:
 * <ol>
 * <li>Look up the bare or relative filename via the injected {@code filenameLookup} (default:
 * {@link GlobalManager#findFilename(String)}). Empty → empty result.</li>
 * <li>If {@code cbs.dsl.source-dir} is unset, return the raw filename.</li>
 * <li>Search {@code source-dir} recursively for a regular file whose name equals the bare filename
 * of step 1; return the source-dir-relative forward-slash path of the first hit.</li>
 * <li>Fall back to the raw filename from step 1 (caller decides what to do with it).</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DslSourcePathResolver {

  private final DslProperties dslProperties;
  private final Function<String, Optional<String>> filenameLookup;

  //TODO: replace ctor with lomboks one
  @Deprecated(forRemoval = true)
  @Autowired
  public DslSourcePathResolver(DslProperties dslProperties) {
    this(dslProperties, name -> GlobalManager.globalManager().findFilename(name));
  }

  /**
   * @param definitionName
   *          the Workbench key (definition name).
   * @return source-dir-relative forward-slash path of the DSL file, or empty if the definition is
   *         unknown (no provider registered and no fallback filename applies).
   */
  public Optional<String> relativePath(String definitionName) {
    if (definitionName == null || definitionName.isBlank()) {
      return Optional.empty();
    }
    Optional<String> resolved = effectiveLookup().apply(definitionName);
    if (resolved.isEmpty() || resolved.get().isBlank()) {
      return Optional.empty();
    }
    String filename = resolved.get();
    String sourceDirProperty = dslProperties.sourceDir();
    if (sourceDirProperty == null || sourceDirProperty.isBlank()) {
      return Optional.of(filename);
    }
    Path root = Path.of(sourceDirProperty).normalize();
    String bareFilename = Path.of(filename).getFileName().toString();
    try (Stream<Path> stream = Files.find(root, Integer.MAX_VALUE,
            (p, _) -> Files.isRegularFile(p) && p.getFileName().toString().equals(bareFilename))) {
      Optional<Path> found = stream.findFirst();
      if (found.isPresent()) {
        return Optional.of(root.relativize(found.get()).toString().replace('\\', '/'));
      }
    } catch (IOException e) {
      log.warn("[DSL source path] failed to resolve {} under {}: {}", definitionName, root,
              e.getMessage());
    }
    return Optional.of(filename);
  }

  /**
   * Returns the stored filename lookup, defaulting to {@link GlobalManager#findFilename(String)}
   * when the lookup is {@code null}. Preserves the null-tolerance the original explicit
   * constructor applied at construction time.
   */
  private Function<String, Optional<String>> effectiveLookup() {
    return filenameLookup == null
            ? name -> GlobalManager.globalManager().findFilename(name)
            : filenameLookup;
  }
}
