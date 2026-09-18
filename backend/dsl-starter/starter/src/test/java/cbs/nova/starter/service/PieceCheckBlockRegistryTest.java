package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.properties.CbsDslManifestProperties;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Behaviour matrix for {@link PieceCheckBlockRegistry}: piece vs principal scoping, TTL expiry, and
 * the operator-initiated clear.
 */
class PieceCheckBlockRegistryTest {

  private final AtomicLong clock = new AtomicLong(1_000_000L);

  @Test
  void pieceScopeBlocksEveryPrincipal() {
    PieceCheckBlockRegistry registry = newRegistry("piece", Duration.ofMinutes(5));

    registry.block("dsl-reload", "auth:alice", "invariant failed");

    assertThat(registry.isBlocked("dsl-reload", "auth:alice")).isTrue();
    assertThat(registry.isBlocked("dsl-reload", "auth:bob")).isTrue();
    assertThat(registry.isBlocked("other-piece", "auth:alice")).isFalse();
  }

  @Test
  void principalScopeBlocksOnlyTheTrippingPrincipal() {
    PieceCheckBlockRegistry registry = newRegistry("principal", Duration.ofMinutes(5));

    registry.block("dsl-reload", "auth:alice", "invariant failed");

    assertThat(registry.isBlocked("dsl-reload", "auth:alice")).isTrue();
    assertThat(registry.isBlocked("dsl-reload", "auth:bob")).isFalse();
  }

  @Test
  void blockExpiresAfterTtl() {
    PieceCheckBlockRegistry registry = newRegistry("piece", Duration.ofMinutes(5));

    registry.block("dsl-reload", "auth:alice", "invariant failed");
    assertThat(registry.isBlocked("dsl-reload", "auth:alice")).isTrue();

    clock.addAndGet(Duration.ofMinutes(5).toMillis() + 1);
    assertThat(registry.isBlocked("dsl-reload", "auth:alice")).isFalse();
    assertThat(registry.activeBlocks()).isEmpty();
  }

  @Test
  void reblockingExtendsTheExpiry() {
    PieceCheckBlockRegistry registry = newRegistry("piece", Duration.ofMinutes(5));

    registry.block("dsl-reload", "auth:alice", "first");
    clock.addAndGet(Duration.ofMinutes(4).toMillis());
    registry.block("dsl-reload", "auth:bob", "second");

    clock.addAndGet(Duration.ofMinutes(4).toMillis());
    assertThat(registry.isBlocked("dsl-reload", "auth:alice")).isTrue();
  }

  @Test
  void clearRemovesAllBlocks() {
    PieceCheckBlockRegistry registry = newRegistry("principal", Duration.ofMinutes(5));
    registry.block("dsl-reload", "auth:alice", "one");
    registry.block("other", "auth:bob", "two");

    registry.clear();

    assertThat(registry.isBlocked("dsl-reload", "auth:alice")).isFalse();
    assertThat(registry.isBlocked("other", "auth:bob")).isFalse();
    assertThat(registry.activeBlocks()).isEmpty();
  }

  @Test
  void activeBlocksCarriesPiecePrincipalAndReason() {
    PieceCheckBlockRegistry registry = newRegistry("principal", Duration.ofMinutes(5));
    registry.block("dsl-reload", "auth:alice", "invariant failed");

    assertThat(registry.activeBlocks()).hasSize(1);
    PieceCheckBlockRegistry.Block block = registry.activeBlocks().get(0);
    assertThat(block.pieceId()).isEqualTo("dsl-reload");
    assertThat(block.principal()).isEqualTo("auth:alice");
    assertThat(block.reason()).contains("invariant");
  }

  private PieceCheckBlockRegistry newRegistry(String scope, Duration ttl) {
    return new PieceCheckBlockRegistry(
            new CbsDslManifestProperties(true, "classpath:piece-manifest.yaml", null, null,
                    new CbsDslManifestProperties.PostCheck(2, ttl, scope)),
            clock::get);
  }
}
