package cbs.nova.starter.service;

import cbs.nova.starter.config.properties.CbsDslManifestProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;

/**
 * In-memory registry of {@code block-next-execution} blocks raised by failed post-check hooks
 * (T550). Backed by a {@link ConcurrentHashMap} with per-block TTL expiry — evaluated lazily on
 * read and swept on write.
 *
 * <p>
 * Scoping is config-chosen via {@code cbs.dsl.manifest.post-check.block-scope}:
 * <ul>
 * <li>{@code piece} (default) — the block is global for the piece id: every principal is denied
 * until the block clears.</li>
 * <li>{@code principal} — the block applies to the (piece id, principal) pair that tripped it;
 * other principals are unaffected.</li>
 * </ul>
 *
 * <p>
 * In-memory by design, mirroring the guard's in-memory rate-limit buckets: enforcement is
 * single-instance. Multi-replica deployments need a shared store (Epic 2 follow-up) — a block on
 * one replica does not deny traffic served by another.
 *
 * <p>
 * Blocks clear three ways: TTL expiry, manifest reload (a successful {@code PieceManifestService}
 * reload clears all blocks — the operator-reviewed "fix and reload" flow), and restart.
 */
@Slf4j
public class PieceCheckBlockRegistry {

  private final Duration ttl;
  private final boolean principalScoped;
  private final LongSupplier clockMillis;
  private final ConcurrentHashMap<String, Block> blocks = new ConcurrentHashMap<>();

  public PieceCheckBlockRegistry(
          CbsDslManifestProperties properties, LongSupplier clockMillis) {
    this.ttl = properties.postCheck().blockTtl();
    this.principalScoped = "principal".equals(properties.postCheck().blockScope());
    this.clockMillis = clockMillis;
  }

  /**
   * Records (or re-arms) a block for the piece. {@code principal} participates in the key only
   * under {@code block-scope: principal}.
   */
  public void block(String pieceId, String principal, String reason) {
    long expiresAt = clockMillis.getAsLong() + ttl.toMillis();
    blocks.put(key(pieceId, principal), new Block(pieceId, principal, reason, expiresAt));
    log.warn("[Piece post-check] piece '{}' BLOCKED until {} (scope={}, reason: {})",
            pieceId, java.time.Instant.ofEpochMilli(expiresAt),
            principalScoped ? "principal:" + principal : "piece", reason);
  }

  public boolean isBlocked(String pieceId, String principal) {
    Block block = blocks.get(key(pieceId, principal));
    if (block == null) {
      return false;
    }
    if (clockMillis.getAsLong() >= block.expiresAtMillis()) {
      blocks.remove(key(pieceId, principal), block);
      return false;
    }
    return true;
  }

  public void clear() {
    int cleared = blocks.size();
    blocks.clear();
    if (cleared > 0) {
      log.info("[Piece post-check] cleared {} active block(s) on manifest reload", cleared);
    }
  }

  public List<Block> activeBlocks() {
    long now = clockMillis.getAsLong();
    List<Block> active = new ArrayList<>();
    for (Block block : blocks.values()) {
      if (now < block.expiresAtMillis()) {
        active.add(block);
      }
    }
    return List.copyOf(active);
  }

  private String key(String pieceId, String principal) {
    return principalScoped ? pieceId + "|" + principal : pieceId;
  }

  public record Block(String pieceId, String principal, String reason, long expiresAtMillis) {
  }
}
