package cbs.nova.starter.service;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.GlobalManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stamps {@code dsl_runs.content_hash} at run submission (T648).
 *
 * <p>
 * Phase 2 content hash complementing {@link RunDefinitionHash} (descriptor identity). The hash is
 * computed ONCE at DSL publish/reload time — a sha256 over the canonicalized source snapshot
 * (sorted {@code path -> content} of every {@code .java} DSL source file) — and cached here keyed
 * by {@code definition name + version}, so run submission is a cheap lookup. Unlike
 * {@code definition_hash}, it changes when the definition LOGIC changes even when the descriptor
 * stays the same.
 */
public final class DefinitionContentHash {

  private static final Logger log = LoggerFactory.getLogger(DefinitionContentHash.class);

  /** name + '\n' + version → 64-char lowercase hex sha256 of the reload source snapshot. */
  private static final ConcurrentMap<String, String> CACHE = new ConcurrentHashMap<>();

  private DefinitionContentHash() {
  }

  /**
   * Publishes the content hash for every definition loaded in a reload. Called once after the
   * candidate {@link GlobalManager} has been atomically swapped in, so the cache never points at a
   * registry that failed to stage. Failures are logged and swallowed: hashing must never break a
   * reload.
   */
  public static void publish(@Nullable Map<String, String> sources,
          @Nullable GlobalManager registry) {
    try {
      if (sources == null || sources.isEmpty() || registry == null) {
        log.debug("[DSL content-hash] skip publish: sources={}, registry={}",
                sources == null ? "null" : "empty", registry == null ? "null" : "present");
        return;
      }
      String hash = snapshotHash(sources);
      List<String> names = new ArrayList<>(registry.processNames());
      names.addAll(registry.transactionNames());
      for (String name : names) {
        Optional<DslDescriptor> descriptor = registry.describeProcess(name)
                .or(() -> registry.describeTransaction(name));
        descriptor.ifPresent(d -> CACHE.put(key(name, d.version()), hash));
      }
      log.info("[DSL content-hash] published content hash for {} definitions", names.size());
    } catch (Exception ex) {
      log.warn("[DSL content-hash] failed to publish content hash: {}", ex.getMessage(), ex);
    }
  }

  /**
   * Resolves the content hash for a runnable process/transaction name.
   *
   * @return 64-char lowercase hex sha256 of the source snapshot the definition was published with,
   *         or {@code null} when no content hash was published for the name+version — a missing
   *         hash must never fail the run.
   */
  static @Nullable String of(@Nullable String processName) {
    if (processName == null || processName.isBlank()) {
      return null;
    }
    try {
      GlobalManager gm = GlobalManager.globalManager();
      Optional<DslDescriptor> descriptor = gm.describeProcess(processName)
              .or(() -> gm.describeTransaction(processName));
      if (descriptor.isEmpty()) {
        log.warn("No DSL descriptor resolved for process '{}': content_hash stays null",
                processName);
        return null;
      }
      return CACHE.get(key(processName, descriptor.get().version()));
    } catch (Exception ex) {
      log.warn("Failed to resolve content hash for process '{}': {} — content_hash stays null",
              processName, ex.getMessage(), ex);
      return null;
    }
  }

  private static String key(String name, @Nullable String version) {
    return name + '\n' + (version == null ? "" : version);
  }

  private static String snapshotHash(Map<String, String> sources) {
    Map<String, String> canonical = new TreeMap<>(sources);
    StringBuilder payload = new StringBuilder();
    canonical.forEach((path, content) -> payload.append(path).append('\n')
            .append(content).append('\n'));
    return sha256Hex(payload.toString().getBytes(StandardCharsets.UTF_8));
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
