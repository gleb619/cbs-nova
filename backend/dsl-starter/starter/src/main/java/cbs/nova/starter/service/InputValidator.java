package cbs.nova.starter.service;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.jsonschema.JsonSchemaGenerator;
import cbs.nova.dsl.model.ObjectDescriptor;
import cbs.nova.dsl.ParameterDescriptor;
import cbs.nova.starter.config.properties.InputValidationProperties;
import cbs.nova.starter.model.ValidationError;
import cbs.nova.starter.validation.JsonSchemaValidator;
import com.github.benmanes.caffeine.cache.Cache;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import cbs.nova.starter.json.DslDescriptorMixIn;

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

  private final JsonMapper descriptorMapper = DslDescriptorMixIn.mapper();

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

  //TODO: no, we need another way, via misc-codegen new method
  @Deprecated(forRemoval = true)
  private DslDescriptor toDescriptor(ExecutableDescriptor helper) {
    var objectDescriptor = new ObjectDescriptor() {
      @Override
      public String name() {
        return helper.name() != null ? helper.name() : "";
      }

      @Override
      public DslObject.DslType type() {
        return DslObject.DslType.FUNCTION;
      }

      @Override
      public String description() {
        return helper.description();
      }

      @Override
      public Class<?> inputType() {
        return helper.inputType();
      }

      @Override
      public Class<?> outputType() {
        return helper.outputType();
      }
    };
    return DslDescriptor.builder()
            .objectDescriptor(objectDescriptor)
            .hasSideEffects(helper.hasSideEffects())
            .parameters(helper.parameters())
            .taskQueue(null)
            .version(null)
            .startToCloseTimeout(null)
            .heartbeatTimeout(null)
            .build();
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
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] serialized = descriptorMapper.writeValueAsBytes(descriptor);
      return bytesToHex(digest.digest(serialized));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize descriptor", e);
    }
  }

  private @NonNull String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
