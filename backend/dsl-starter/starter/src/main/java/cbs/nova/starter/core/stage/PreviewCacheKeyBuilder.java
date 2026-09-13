package cbs.nova.starter.core.stage;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.starter.model.PreviewModels.PreviewCacheKey;
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
                    .map(helper -> DslDescriptor.builder()
                            .name(name)
                            .type(DslObject.DslType.FUNCTION)
                            .description(helper.description())
                            .inputType(helper.inputType())
                            .outputType(helper.outputType())
                            .hasSideEffects(helper.hasSideEffects())
                            .parameters(helper.parameters())
                            .taskQueue(null) // helpers are not Temporal-scheduled; no task queue
                                             // applies (dropped from hash by non_null inclusion)
                            .version(null) // version is a Temporal workflow/activity concept;
                                           // helpers carry no version (dropped from hash by
                                           // non_null inclusion)
                            .startToCloseTimeout(null) // helpers run in-process; no Temporal
                                                       // activity start-to-close timeout applies
                                                       // (dropped from hash by non_null inclusion)
                            .heartbeatTimeout(null) // helpers run in-process; no Temporal activity
                                                    // heartbeat applies (dropped from hash by
                                                    // non_null inclusion)
                            .build()));
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
