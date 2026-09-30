package cbs.nova.starter.core.stage;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.model.Descriptors;
import cbs.nova.starter.model.PreviewModels.PreviewCacheKey;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.json.JsonMapper;
import cbs.nova.starter.json.DslDescriptorMapper;

final class PreviewCacheKeyBuilder {

  private final JsonMapper jsonMapper = DslDescriptorMapper.mapper();

  PreviewCacheKey build(@NonNull String name, @NonNull Context<?> ctx) {
    GlobalManager gm = GlobalManager.globalManager();
    Optional<DslDescriptor> descriptor = gm.describeProcess(name)
            .or(() -> gm.describeTransaction(name))
            .or(() -> gm.describeHelper(name)
                    .map(helper -> Descriptors.from(name, helper)));
    String dslHash = descriptor.map(this::dslDescriptorHash).orElse("");
    String inputHash = inputHash(ctx);
    return new PreviewCacheKey(name, dslHash, inputHash);
  }

  private @NonNull String dslDescriptorHash(@NonNull DslDescriptor descriptor) {
    try {
      byte[] bytes = jsonMapper.writeValueAsBytes(descriptor);
      return sha256Hex(bytes);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize DSL descriptor", e);
    }
  }

  /**
   * Hashes the request body together with the seeded signal timeline (T736). Without the timeline
   * in the key, a seeded preview could be served from a cache entry produced by an unseeded preview
   * of the same input (and vice versa).
   */
  private @NonNull String inputHash(@NonNull Context<?> ctx) {
    try {
      Map<String, Object> requestShape = new LinkedHashMap<>();
      requestShape.put("input", ctx.body());
      requestShape.put("signals", ctx.metadata().get(Constants.PREVIEW_SIGNAL_SEEDS_KEY));
      byte[] bytes = jsonMapper.writeValueAsBytes(requestShape);
      return sha256Hex(bytes);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize preview input", e);
    }
  }

  private @NonNull String sha256Hex(@NonNull byte[] input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input);
      StringBuilder sb = new StringBuilder();
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
