package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.fake.FakeConfig;
import cbs.nova.dsl.fake.FakeEntry;
import org.junit.jupiter.api.Test;

class CbsNovaFakesPropertiesTest {

  @Test
  void defaultsAreEnabledAndEmptyConfig() {
    var props = new CbsNovaFakesProperties(true, null);
    assertThat(props.enabled()).isTrue();
    assertThat(props.config()).isEqualTo(FakeConfig.empty());
    assertThat(props.config().entries()).isEmpty();
  }

  @Test
  void customBindingIsPreserved() {
    var entry = new FakeEntry("helper", "httpCall", "fake-body");
    var config = FakeConfig.of(entry);
    var props = new CbsNovaFakesProperties(true, config);

    assertThat(props.enabled()).isTrue();
    assertThat(props.config().entries()).containsExactly(entry);
    assertThat(props.config().findResponse("helper", "httpCall")).isEqualTo("fake-body");
  }

  @Test
  void enabledFlagControlsFaking() {
    assertThat(new CbsNovaFakesProperties(true, FakeConfig.empty()).enabled()).isTrue();
    assertThat(new CbsNovaFakesProperties(false, FakeConfig.empty()).enabled()).isFalse();
  }

  @Test
  void emptyConfigFindResponseReturnsNull() {
    var props = new CbsNovaFakesProperties(true, FakeConfig.empty());
    assertThat(props.config().findResponse("helper", "anything")).isNull();
  }
}
