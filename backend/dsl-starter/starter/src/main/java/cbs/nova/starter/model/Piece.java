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
        String failMode,
        ObjectAllow allow,
        ObjectDeny deny) {

  public Piece {
    Objects.requireNonNull(id, "id required");
    Objects.requireNonNull(target, "target required");
    preCheck = preCheck == null ? List.of() : List.copyOf(preCheck);
    postCheck = postCheck == null ? List.of() : List.copyOf(postCheck);
    failMode = failMode == null ? "deny" : failMode;
    allow = allow == null ? new ObjectAllow(null, null, null) : allow;
    deny = deny == null ? new ObjectDeny(null, null, null) : deny;
  }

  /** Back-compat constructor for api/button pieces that carry no object allow/deny data. */
  public Piece(
          String id,
          Target target,
          List<PreCheck> preCheck,
          List<PostCheck> postCheck,
          String failMode) {
    this(id, target, preCheck, postCheck, failMode, null, null);
  }
}
