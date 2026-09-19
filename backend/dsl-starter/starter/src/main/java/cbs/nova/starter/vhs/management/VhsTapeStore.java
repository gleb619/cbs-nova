package cbs.nova.starter.vhs.management;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NonNull;

/**
 * Abstraction for discovering, reading and deleting recorded VHS tape files.
 *
 * <p>
 * Today only a local file-system implementation exists. An object-storage variant is a planned
 * follow-up.
 */
public interface VhsTapeStore {

  /**
   * List every tape known to the store, newest first.
   */
  @NonNull
  List<TapeSummary> list();

  /**
   * Find the tape whose recorded {@code runId} matches the given identifier.
   */
  @NonNull
  Optional<TapeSummary> findByRunId(@NonNull String runId);

  /**
   * Find the file-system path for the tape matching {@code runId}.
   */
  @NonNull
  Optional<Path> findPath(@NonNull String runId);

  /**
   * Open an input stream for the tape matching {@code runId}.
   *
   * @return an open stream, or {@link Optional#empty()} if the tape is missing
   */
  @NonNull
  Optional<InputStream> openStream(@NonNull String runId);

  /**
   * Delete the tape matching {@code runId}.
   *
   * @return {@code true} when a file was actually removed
   */
  boolean delete(@NonNull String runId);
}
