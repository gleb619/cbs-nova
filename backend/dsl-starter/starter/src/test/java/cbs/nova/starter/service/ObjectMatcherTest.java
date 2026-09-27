package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.model.DslIntrospectionModels.HelperCatalogEntry;
import org.junit.jupiter.api.Test;

class ObjectMatcherTest {

  @Test
  void exactMatchesDescriptionSubstring() {
    var entry = new HelperCatalogEntry("MathHelper", "arithmetic operations", "Integer", "Long");
    assertThat(ObjectMatcher.exact().matches("arithmetic", entry)).isTrue();
  }

  @Test
  void fuzzyMatchesTypo() {
    var entry = new HelperCatalogEntry("Helper", "description", "String", "Integer");
    assertThat(ObjectMatcher.fuzzy().matches("helpr", entry)).isTrue();
  }

  @Test
  void fuzzyRejectsDistantString() {
    var entry = new HelperCatalogEntry("Helper", "description", "String", "Integer");
    assertThat(ObjectMatcher.fuzzy().matches("xyzabc", entry)).isFalse();
  }

  @Test
  void cosineMatchesSharedTerms() {
    var entry = new HelperCatalogEntry("math-helper", "arithmetic operations", "Integer", "Long");
    assertThat(ObjectMatcher.cosine().matches("math helper", entry)).isTrue();
  }

  @Test
  void cosineRejectsUnrelatedTerms() {
    var entry = new HelperCatalogEntry("foo-bar", "baz qux", "String", "Boolean");
    assertThat(ObjectMatcher.cosine().matches("nomatch", entry)).isFalse();
  }
}
