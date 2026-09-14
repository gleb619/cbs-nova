package cbs.nova.starter.service;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.starter.json.DslDescriptorMixIn;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stamps {@code dsl_runs.definition_hash} at run submission (T492).
 *
 * <p>
 * Phase 1 uses DESCRIPTOR identity — the same sha256 over the Jackson-serialized
 * {@link DslDescriptor} (taskQueue / version / timeouts) that the preview cache keys on — not full
 * logic identity: two functionally different definitions with the same descriptor collide. A true
 * content hash computed at publish/reload time is a planned Epic 5 follow-up.
 */
final class RunDefinitionHash {

  private static final Logger log = LoggerFactory.getLogger(RunDefinitionHash.class);

  private static final JsonMapper JSON_MAPPER = DslDescriptorMixIn.mapper();

  private RunDefinitionHash() {
  }

  /**
   * Resolves the descriptor hash for a runnable process/transaction name.
   *
   * @return 64-char lowercase hex sha256, or {@code null} when the definition is not resolvable or
   *         hashing fails — a missing hash must never fail the run.
   */
  static @Nullable String of(@Nullable String processName) {
    if (processName == null || processName.isBlank()) {
      return null;
    }
    GlobalManager gm = GlobalManager.globalManager();
    Optional<DslDescriptor> descriptor = gm.describeProcess(processName)
            .or(() -> gm.describeTransaction(processName));
    if (descriptor.isEmpty()) {
      log.warn("No DSL descriptor resolved for process '{}': definition_hash stays null",
              processName);
      return null;
    }
    try {
      return sha256Hex(JSON_MAPPER.writeValueAsBytes(descriptor.get()));
    } catch (Exception ex) {
      log.warn("Failed to hash DSL descriptor for process '{}': {} — definition_hash "
              + "stays null", processName, ex.getMessage(), ex);
      return null;
    }
  }

  private static String sha256Hex(byte[] input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(input));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
