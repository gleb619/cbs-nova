package cbs.nova.starter.service;

import cbs.nova.starter.model.DslIntrospectionModels.HelperCatalogEntry;
import java.util.Locale;

@FunctionalInterface
interface ObjectMatcher {

  boolean matches(String term, HelperCatalogEntry entry);

  static ObjectMatcher exact() {
    return (term, entry) -> {
      String candidate = (entry.name() + " "
              + (entry.description() == null ? "" : entry.description()))
              .toLowerCase(Locale.ROOT);
      return candidate.contains(term);
    };
  }

  static ObjectMatcher all() {
    return (_, _) -> Boolean.TRUE;
  }

}
