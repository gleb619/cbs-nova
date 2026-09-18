package cbs.nova.starter.model;

import java.util.List;
import java.util.Objects;

/**
 * A single declarative piece of the cbs-nova control plane. Pieces are immutable; the service holds
 * a snapshot of the whole manifest and exposes indexed lookups.
 */
public record Piece(
        String id,
        Target target,
        List<PreCheck> preCheck,
        List<PostCheck> postCheck,
        String failMode) {

  public Piece {
    Objects.requireNonNull(id, "id required");
    Objects.requireNonNull(target, "target required");
    preCheck = preCheck == null ? List.of() : List.copyOf(preCheck);
    postCheck = postCheck == null ? List.of() : List.copyOf(postCheck);
    failMode = failMode == null ? "deny" : failMode;
  }
}
