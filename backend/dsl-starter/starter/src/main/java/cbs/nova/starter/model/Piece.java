package cbs.nova.starter.model;

import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.RequiredArgsConstructor;

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

  @Builder
  @RequiredArgsConstructor
  private static final class ConstructorArgs {

    private final String id;
    private final Target target;
    private final List<PreCheck> preCheck;
    private final List<PostCheck> postCheck;
    private final String failMode;
    private final ObjectAllow allow;
    private final ObjectDeny deny;
  }

  public Piece {
    ConstructorArgs args = new ConstructorArgs(id, target, preCheck, postCheck, failMode, allow,
            deny);
    id = Objects.requireNonNull(args.id, "id required");
    target = Objects.requireNonNull(args.target, "target required");
    preCheck = args.preCheck == null ? List.of() : List.copyOf(args.preCheck);
    postCheck = args.postCheck == null ? List.of() : List.copyOf(args.postCheck);
    failMode = args.failMode == null ? "deny" : args.failMode;
    allow = args.allow == null ? new ObjectAllow(null, null, null) : args.allow;
    deny = args.deny == null ? new ObjectDeny(null, null, null) : args.deny;
  }

  public Piece(
          String id,
          Target target,
          List<PreCheck> preCheck,
          List<PostCheck> postCheck,
          String failMode) {
    this(id, target, preCheck, postCheck, failMode, null, null);
  }
}
