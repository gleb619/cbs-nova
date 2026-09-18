package cbs.nova.starter.model;

import java.util.Set;

/**
 * Deny-list scope carried by an {@code object}-target piece. When the executing definition matches
 * the scope the helper/process/function invocation is rejected with {@code CAPABILITY_DENIED}.
 */
public record ObjectDeny(
        Set<String> definitions,
        Set<String> helpers,
        Set<String> capabilities) {

  public ObjectDeny {
    definitions = definitions == null ? Set.of() : Set.copyOf(definitions);
    helpers = helpers == null ? Set.of() : Set.copyOf(helpers);
    capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
  }
}
