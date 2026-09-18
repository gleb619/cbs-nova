package cbs.nova.starter.model;

import java.util.Collections;
import java.util.Set;

/**
 * Allow-list grant carried by an {@code object}-target piece. A definition matches the grant when
 * its name appears in {@code definitions} (or {@code definitions} is empty, i.e. wildcard), or when
 * any of its declared {@code capabilities} overlaps with {@code capabilities}, or when the invoked
 * object name is listed in {@code helpers}.
 */
public record ObjectAllow(
        Set<String> definitions,
        Set<String> helpers,
        Set<String> capabilities) {

  public ObjectAllow {
    definitions = definitions == null ? Set.of() : Set.copyOf(definitions);
    helpers = helpers == null ? Set.of() : Set.copyOf(helpers);
    capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
  }
}
