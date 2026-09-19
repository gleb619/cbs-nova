package cbs.nova.starter.security;

import static cbs.nova.starter.core.StarterConstants.ACTION_OBJECT_GUARD_DENY;
import static cbs.nova.starter.core.StarterConstants.ACTION_OBJECT_GUARD_PERMISSIVE;
import static cbs.nova.starter.core.StarterConstants.OBJECT_GUARD_DENIED_COUNTER;
import static cbs.nova.starter.core.StarterConstants.OBJECT_TYPE_TAG;
import static cbs.nova.starter.core.StarterConstants.OUTCOME_FAILURE;
import static cbs.nova.starter.core.StarterConstants.OUTCOME_SUCCESS;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.starter.config.properties.CbsDslManifestProperties;
import cbs.nova.starter.model.CapabilityDeclaration;
import cbs.nova.starter.model.ObjectAllow;
import cbs.nova.starter.model.ObjectDeny;
import cbs.nova.starter.model.Piece;
import cbs.nova.starter.model.Target;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.service.DslCapabilityRegistry;
import cbs.nova.starter.service.PieceManifestService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Manifest-based guard for DSL object invocations (helper/process/function). Consults the same
 * {@link PieceManifestService} loader as the API/button guards so the Epic 2 sandboxing allowlist
 * is authored once and enforced in both preview/explain and production runs.
 *
 * <p>
 * The guard is a no-op when {@code cbs.dsl.manifest.object-enforcement.enabled} is {@code false}
 * (the shipped default). When enabled, preview is deny-by-default; production follows the
 * {@code cbs.dsl.manifest.object-mode} policy ({@code permissive} = allow+audit unlisted objects,
 * {@code strict} = deny-by-default).
 */
@Slf4j
@RequiredArgsConstructor
public final class ManifestObjectGuard {

  private final CbsDslManifestProperties properties;
  private final PieceManifestService manifestService;
  private final ObjectProvider<DslAuditService> auditServiceProvider;
  private final ObjectProvider<MeterRegistry> meterRegistryProvider;
  private final ObjectProvider<DslCapabilityRegistry> capabilityRegistryProvider;

  /**
   * Evaluates whether the invocation of {@code objectType:objectName} on behalf of
   * {@code definitionName} is allowed.
   *
   * @return {@link Optional#empty()} when the invocation is allowed, or a {@link Denial} carrying
   *         the piece id and reason when it must be rejected.
   */
  public boolean isActive() {
    return properties.objectEnforcement().enabled();
  }

  public @NonNull Optional<Denial> check(
          @NonNull ExecutionMode mode,
          @Nullable String definitionName,
          @NonNull String objectType,
          @NonNull String objectName,
          @Nullable String correlationId) {

    if (!properties.objectEnforcement().enabled()) {
      return Optional.empty();
    }

    CapabilityDeclaration declared = capabilityDeclarationFor(definitionName);
    List<Piece> pieces = manifestService.findByObject(objectType, objectName);

    Optional<Denial> explicitDeny = pieces.stream()
            .filter(p -> !p.deny().definitions().isEmpty()
                    || !p.deny().helpers().isEmpty()
                    || !p.deny().capabilities().isEmpty())
            .filter(p -> matches(p.deny(), definitionName, objectName, declared))
            .findFirst()
            .map(p -> new Denial(definitionName, p.id(), objectType, objectName,
                    "explicit deny piece matched"));

    if (explicitDeny.isPresent()) {
      recordDenial(mode, explicitDeny.get(), correlationId);
      return explicitDeny;
    }

    Optional<Piece> allowPiece = pieces.stream()
            .filter(p -> matches(p.allow(), definitionName, objectName, declared))
            .findFirst();

    if (allowPiece.isPresent()) {
      return Optional.empty();
    }

    if (!pieces.isEmpty()) {
      // Object is known to the manifest but not allow-listed for this definition.
      Denial denial = new Denial(definitionName, null, objectType, objectName,
              "object not allow-listed for definition " + safe(definitionName));
      recordDenial(mode, denial, correlationId);
      return Optional.of(denial);
    }

    // No object piece covers this construct: apply the production default policy. Preview is
    // always deny-by-default when enforcement is on.
    if (mode == ExecutionMode.PREVIEW || mode == ExecutionMode.EXPLAIN
            || properties.objectMode() == CbsDslManifestProperties.ObjectMode.STRICT) {
      Denial denial = new Denial(definitionName, null, objectType, objectName,
              "no manifest piece covers this object");
      recordDenial(mode, denial, correlationId);
      return Optional.of(denial);
    }

    // Permissive production default: allow, but audit the unlisted invocation.
    recordPermissiveAllow(mode, definitionName, objectType, objectName, correlationId);
    return Optional.empty();
  }

  private @NonNull CapabilityDeclaration capabilityDeclarationFor(@Nullable String definitionName) {
    DslCapabilityRegistry registry = capabilityRegistryProvider.getIfAvailable();
    if (registry == null || definitionName == null || definitionName.isBlank()) {
      return CapabilityDeclaration.empty();
    }
    try {
      return registry.forDefinition(definitionName);
    } catch (RuntimeException e) {
      log.warn("Capability registry failed for definition '{}': {}", definitionName,
              e.getMessage());
      return CapabilityDeclaration.empty();
    }
  }

  private boolean matches(
          @NonNull ObjectAllow allow,
          @Nullable String definitionName,
          @NonNull String objectName,
          @NonNull CapabilityDeclaration declared) {
    return matchesScope(allow.definitions(), allow.helpers(), allow.capabilities(),
            definitionName, objectName, declared);
  }

  private boolean matches(
          @NonNull ObjectDeny deny,
          @Nullable String definitionName,
          @NonNull String objectName,
          @NonNull CapabilityDeclaration declared) {
    return matchesScope(deny.definitions(), deny.helpers(), deny.capabilities(),
            definitionName, objectName, declared);
  }

  private boolean matchesScope(
          @NonNull Set<String> definitions,
          @NonNull Set<String> helpers,
          @NonNull Set<String> capabilities,
          @Nullable String definitionName,
          @NonNull String objectName,
          @NonNull CapabilityDeclaration declared) {
    if (!helpers.isEmpty() && helpers.contains(objectName)) {
      return true;
    }
    if (!definitions.isEmpty()
            && definitionName != null
            && definitions.contains(definitionName)) {
      return true;
    }
    if (!capabilities.isEmpty()) {
      Set<String> intersection = new LinkedHashSet<>(declared.capabilities());
      intersection.retainAll(capabilities);
      if (!intersection.isEmpty()) {
        return true;
      }
    }
    // Empty scope = wildcard: a deny piece with no scope denies everyone, an allow piece with no
    // scope allows everyone.
    return definitions.isEmpty() && helpers.isEmpty() && capabilities.isEmpty();
  }

  private void recordDenial(
          @NonNull ExecutionMode mode,
          @NonNull Denial denial,
          @Nullable String correlationId) {
    log.warn("[Object guard] denied {}:{} for definition={} piece={} reason={} correlationId={}",
            denial.objectType(), denial.objectName(), denial.definitionName(),
            denial.pieceId(), denial.reason(), correlationId);
    MeterRegistry registry = meterRegistryProvider.getIfAvailable();
    if (registry != null) {
      registry.counter(OBJECT_GUARD_DENIED_COUNTER, OBJECT_TYPE_TAG, denial.objectType())
              .increment();
    }
    DslAuditService audit = auditServiceProvider.getIfAvailable();
    if (audit == null) {
      return;
    }
    Map<String, Object> details = new HashMap<>();
    details.put("objectType", denial.objectType());
    details.put("objectName", denial.objectName());
    details.put("definitionName", denial.definitionName());
    details.put("pieceId", denial.pieceId());
    details.put("reason", denial.reason());
    details.put("mode", mode.name());
    audit.record(DslAuditService.currentActor(), ACTION_OBJECT_GUARD_DENY,
            denial.objectType() + ":" + denial.objectName(), correlationId, OUTCOME_FAILURE,
            details);
  }

  private void recordPermissiveAllow(
          @NonNull ExecutionMode mode,
          @Nullable String definitionName,
          @NonNull String objectType,
          @NonNull String objectName,
          @Nullable String correlationId) {
    log.info("[Object guard] permissive allow {}:{} for definition={} mode={} correlationId={}",
            objectType, objectName, definitionName, mode.name(), correlationId);
    DslAuditService audit = auditServiceProvider.getIfAvailable();
    if (audit == null) {
      return;
    }
    Map<String, Object> details = new HashMap<>();
    details.put("objectType", objectType);
    details.put("objectName", objectName);
    details.put("definitionName", definitionName);
    details.put("mode", mode.name());
    audit.record(DslAuditService.currentActor(), ACTION_OBJECT_GUARD_PERMISSIVE,
            objectType + ":" + objectName, correlationId, OUTCOME_SUCCESS, details);
  }

  private @NonNull String safe(@Nullable String value) {
    return value == null || value.isBlank() ? "<unknown>" : value;
  }

  /**
   * Rejection context returned by {@link #check}.
   */
  public record Denial(
          @Nullable String definitionName,
          @Nullable String pieceId,
          @NonNull String objectType,
          @NonNull String objectName,
          @NonNull String reason) {
  }
}
