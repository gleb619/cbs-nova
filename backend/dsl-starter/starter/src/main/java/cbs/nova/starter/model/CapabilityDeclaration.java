package cbs.nova.starter.model;

import java.util.Set;

/**
 * Capabilities declared by a DSL definition/module: named helpers it intends to call, plus
 * capability classes such as {@code network} or {@code filesystem}. The manifest object-target
 * pieces are evaluated against this declaration by {@code ManifestObjectGuard}.
 */
public record CapabilityDeclaration(
        Set<String> helpers,
        Set<String> capabilities) {

  public CapabilityDeclaration {
    helpers = helpers == null ? Set.of() : Set.copyOf(helpers);
    capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
  }

  public static CapabilityDeclaration empty() {
    return new CapabilityDeclaration(Set.of(), Set.of());
  }
}
