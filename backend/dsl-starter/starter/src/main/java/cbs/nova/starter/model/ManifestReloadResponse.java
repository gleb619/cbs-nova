package cbs.nova.starter.model;

import java.util.List;

/**
 * Response body for {@code POST /api/dsl/manifest/reload}. On success {@code pieceCount} reflects
 * the swapped snapshot and {@code errors} is empty. On rejection {@code pieceCount} is zero and
 * {@code errors} names each offending piece id and field.
 */
public record ManifestReloadResponse(int pieceCount, List<ErrorEntry> errors) {

  public ManifestReloadResponse {
    errors = errors == null ? List.of() : List.copyOf(errors);
  }

  /**
   * Per-piece validation error. The previous snapshot stays live when any error is present.
   */
  public record ErrorEntry(String pieceId, String field, String message) {
  }
}
