package cbs.nova.starter.service;

import cbs.nova.starter.model.DslIntrospectionModels.HelperCatalogEntry;
import java.util.Locale;

@FunctionalInterface
interface HelperMatcher {

  boolean matches(String term, HelperCatalogEntry entry);

  static HelperMatcher exact() {
    return (term, entry) -> {
      String candidate = (entry.name() + " "
                          + (entry.description() == null ? "" : entry.description()))
          .toLowerCase(Locale.ROOT);
      return candidate.contains(term);
    };
  }

}
