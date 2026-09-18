package cbs.nova.starter.service;

import cbs.nova.starter.model.CapabilityDeclaration;
import org.jspecify.annotations.NonNull;

/**
 * Resolves the capability declaration for a named DSL definition/module. The default registry
 * returns an empty declaration, which makes {@code ManifestObjectGuard} fall back to the manifest
 * allow/deny lists keyed by definition name. Custom registries can feed richer declarations from
 * DSL source metadata.
 */
public interface DslCapabilityRegistry {

  @NonNull
  CapabilityDeclaration forDefinition(@NonNull String definitionName);
}
