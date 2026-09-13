package cbs.nova.dsl.explain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ClasspathExplainResourceResolverTest {

  private final ClasspathExplainResourceResolver resolver = new ClasspathExplainResourceResolver(
          ClasspathExplainResourceResolver.DEFAULT_PREFIX);

  @Test
  void loadsPlainResourceName() {
    assertThat(resolver.load("builder-sample.md")).contains("# Builder Sample");
  }

  @Test
  void loadsResourceNameWithLeadingSlash() {
    assertThat(resolver.load("/builder-sample.md")).contains("# Builder Sample");
  }

  @Test
  void loadsPrefixedResourceName() {
    assertThat(resolver.load("explain/builder-sample.md")).contains("# Builder Sample");
  }

  @Test
  void throwsWhenResourceMissing() {
    assertThatThrownBy(() -> resolver.load("does-not-exist.md"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("classpath: explain/does-not-exist.md");
  }

  @Test
  void throwsWhenPathIsBlank() {
    assertThatThrownBy(() -> resolver.load("  "))
            .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void prefixIsConfigurable() {
    var prefixed = new ClasspathExplainResourceResolver("custom/");

    assertThatThrownBy(() -> prefixed.load("builder-sample.md"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("classpath: custom/builder-sample.md");
  }
}
