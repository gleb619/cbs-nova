package cbs.nova.starter.exception;

import cbs.nova.starter.model.ManifestReloadResponse;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Fail-fast exception thrown when the piece-manifest YAML contains malformed or semantically
 * invalid entries. The message names the first offending piece id and field; {@link #errors()}
 * carries the full list for reload responses.
 */
@RequiredArgsConstructor
public class PieceManifestValidationException extends IllegalStateException {

  private final @NonNull List<ManifestReloadResponse.ErrorEntry> errors;

  public List<ManifestReloadResponse.ErrorEntry> errors() {
    return List.copyOf(errors);
  }

  @Override
  public String getMessage() {
    if (errors.isEmpty()) {
      return "piece manifest validation failed";
    }
    var first = errors.get(0);
    return "piece manifest validation failed for piece '" + first.pieceId() + "' field "
            + first.field() + ": " + first.message();
  }
}
