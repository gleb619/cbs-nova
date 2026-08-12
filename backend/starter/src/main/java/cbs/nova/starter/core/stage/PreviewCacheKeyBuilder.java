package cbs.nova.starter.core.stage;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.starter.cache.PreviewCacheKey;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

final class PreviewCacheKeyBuilder {

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  PreviewCacheKey build(@NonNull String name, @NonNull Context<?> ctx) {
    GlobalManager gm = GlobalManager.globalManager();
    Optional<DslDescriptor> descriptor = gm.describeProcess(name)
            .or(() -> gm.describeTransaction(name))
            .or(() -> gm.describeHelper(name)
                    .map(helper -> new DslDescriptor(
                            name,
                            DslObject.DslType.FUNCTION,
                            helper.description(),
                            helper.inputType(),
                            helper.outputType(),
                            false,
                            helper.hasSideEffects(),
                            helper.previewBehavior(),
                            helper.parameters(),
                            null,
                            null,
                            null,
                            null)));
    String dslHash = descriptor.map(this::dslDescriptorHash).orElse("");
    String inputHash = inputHash(ctx.body());
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

  private @NonNull String inputHash(@Nullable Object input) {
    try {
      byte[] bytes = input == null
              ? "null".getBytes(StandardCharsets.UTF_8)
              : jsonMapper.writeValueAsBytes(input);
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
