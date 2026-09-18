package cbs.nova.starter.exception;

import cbs.nova.starter.model.ManifestReloadResponse;
import java.util.List;

/**
 * Fail-fast exception thrown when the piece-manifest YAML contains malformed or semantically
 * invalid entries. The message names the first offending piece id and field; {@link #errors()}
 * carries the full list for reload responses.
 */
public class PieceManifestValidationException extends IllegalStateException {

  private final List<ManifestReloadResponse.ErrorEntry> errors;

  public PieceManifestValidationException(List<ManifestReloadResponse.ErrorEntry> errors) {
    super(formatMessage(errors));
    this.errors = List.copyOf(errors);
  }

  public List<ManifestReloadResponse.ErrorEntry> errors() {
    return errors;
  }

  private static String formatMessage(List<ManifestReloadResponse.ErrorEntry> errors) {
    if (errors.isEmpty()) {
      return "piece manifest validation failed";
    }
    var first = errors.get(0);
    return "piece manifest validation failed for piece '" + first.pieceId() + "' field "
            + first.field() + ": " + first.message();
  }
}
