package cbs.nova.starter.security;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.properties.CbsDslManifestProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Properties-backed default of {@link FeatureFlagSource}: enabled only when the flag is explicitly
 * bound to {@code true} under {@code cbs.dsl.manifest.flags.<name>}.
 */
class FeatureFlagSourceTest {

  private final CbsDslManifestProperties properties = new CbsDslManifestProperties(true,
          "classpath:piece-manifest.yaml",
          Map.of("workbench-publish", true, "retired-feature", false), null);
  private final PropertiesFeatureFlagSource source = new PropertiesFeatureFlagSource(properties);
  private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");

  @Test
  void flagEnabledWhenPropertyIsTrue() {
    assertThat(source.isEnabled("workbench-publish", request)).isTrue();
  }

  @Test
  void flagDisabledWhenPropertyIsFalse() {
    assertThat(source.isEnabled("retired-feature", request)).isFalse();
  }

  @Test
  void flagDisabledWhenAbsentFromProperties() {
    assertThat(source.isEnabled("never-configured", request)).isFalse();
  }

  @Test
  void blankFlagNameIsDisabled() {
    assertThat(source.isEnabled(" ", request)).isFalse();
  }

  @Test
  void nullPropertiesMapsMeanNoFlags() {
    var empty = new PropertiesFeatureFlagSource(
            new CbsDslManifestProperties(true, "classpath:piece-manifest.yaml"));
    assertThat(empty.isEnabled("anything", request)).isFalse();
  }
}
