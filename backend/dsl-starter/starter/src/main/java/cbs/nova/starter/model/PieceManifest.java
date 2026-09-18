package cbs.nova.starter.model;

import java.util.List;

/**
 * Top-level document loaded from the piece-manifest YAML. Contains an ordered list of pieces.
 */
public record PieceManifest(List<Piece> pieces) {

  public PieceManifest {
    pieces = pieces == null ? List.of() : List.copyOf(pieces);
  }
}
