package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CbsNovaCachePropertiesTest {

  @Test
  void specForFallsBackToDefaultsWhenNoOverridePresent() {
    var props = new CbsNovaCacheProperties(null, null);

    var spec = props.specFor("not-configured");

    assertThat(spec.ttl()).isEqualTo(Duration.ofMinutes(5));
    assertThat(spec.maxSize()).isEqualTo(10_000L);
  }

  @Test
  void specForReturnsOverrideWhenConfigured() {
    var override = new CbsNovaCacheProperties.CacheSpec(Duration.ofSeconds(30), 500L);
    var props = new CbsNovaCacheProperties(
            new CbsNovaCacheProperties.Defaults(Duration.ofMinutes(1), 100L),
            Map.of(CbsNovaCacheProperties.Names.PREVIEW_RESULT, override));

    var spec = props.specFor(CbsNovaCacheProperties.Names.PREVIEW_RESULT);

    assertThat(spec).isEqualTo(override);
  }

  @Test
  void specForStillReturnsDefaultsForUnconfiguredNameWhenOtherOverridesExist() {
    var override = new CbsNovaCacheProperties.CacheSpec(Duration.ofSeconds(30), 500L);
    var defaults = new CbsNovaCacheProperties.Defaults(Duration.ofMinutes(2), 250L);
    var props = new CbsNovaCacheProperties(
            defaults,
            Map.of(CbsNovaCacheProperties.Names.PREVIEW_RESULT, override));

    var spec = props.specFor(CbsNovaCacheProperties.Names.MAP_INPUT_ADAPTERS);

    assertThat(spec.ttl()).isEqualTo(defaults.ttl());
    assertThat(spec.maxSize()).isEqualTo(defaults.maxSize());
  }
}
