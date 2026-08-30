package cbs.nova.starter.service;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.JsonSchemaGenerator;
import cbs.nova.dsl.ParameterDescriptor;
import cbs.nova.starter.config.properties.InputValidationProperties;
import cbs.nova.starter.model.ValidationError;
import cbs.nova.starter.validation.JsonSchemaValidator;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves the target construct, generates/caches its input JSON schema, and validates the request
 * body against that schema before execution.
 *
 * <p>
 * Unknown construct names are ignored ({@link GlobalManager} lookup returns empty), leaving the
 * existing 404/error semantics untouched. Non-record input types are accepted without shape checks
 * because the schema generator only infers constraints for Java records.
 */
@Service
@RequiredArgsConstructor
public class InputValidator {

  private final JsonSchemaGenerator schemaGenerator;
  private final InputValidationProperties properties;
  private final Cache<String, Map<String, Object>> schemaCache;

  private final JsonMapper descriptorMapper = JsonMapper.builder().build();

  public List<ValidationError> validate(String constructName, Object body) {
    if (!properties.enabled()) {
      return List.of();
    }

    Optional<DslDescriptor> descriptor = resolveDescriptor(constructName);
    if (descriptor.isEmpty()) {
      return List.of();
    }

    DslDescriptor d = descriptor.get();
    Map<String, Object> schema = schemaCache.get(cacheKey(constructName, d), _ -> buildSchema(d));
    return JsonSchemaValidator.validate(body, schema);
  }

  private Optional<DslDescriptor> resolveDescriptor(String name) {
    GlobalManager gm = GlobalManager.globalManager();
    return gm.describeProcess(name)
            .or(() -> gm.describeTransaction(name))
            .or(() -> gm.describeHelper(name).map(this::toDescriptor));
  }

  private DslDescriptor toDescriptor(cbs.nova.dsl.ExecutableDescriptor helper) {
    return new DslDescriptor(
            helper.name() != null ? helper.name() : "",
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
            null);
  }

  private @NonNull String cacheKey(String constructName, DslDescriptor descriptor) {
    return constructName + "|" + descriptorHash(descriptor);
  }

  private Map<String, Object> buildSchema(DslDescriptor descriptor) {
    Class<?> inputType = descriptor.inputType();
    List<ParameterDescriptor> parameters = descriptor.parameters();
    if (inputType != null && inputType.isRecord()) {
      return schemaGenerator.generateSchema(inputType);
    }
    if (inputType != null) {
      return Map.of("type", "any");
    }
    if (parameters != null && !parameters.isEmpty()) {
      return schemaGenerator.generateSchema(parameters);
    }
    return Map.of("type", "any");
  }

  private @NonNull String descriptorHash(DslDescriptor descriptor) {
    try {
      byte[] bytes = descriptorMapper.writeValueAsBytes(descriptor);
      return sha256Hex(bytes);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize DSL descriptor", e);
    }
  }

  private @NonNull String sha256Hex(byte[] input) {
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
