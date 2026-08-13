package cbs.nova.starter.config;

import cbs.nova.dsl.fake.FakeConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "cbs.nova.fakes")
public record CbsNovaFakesProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue FakeConfig config) {

  public CbsNovaFakesProperties {
    config = config == null ? FakeConfig.empty() : config;
  }
}
