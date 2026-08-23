package cbs.nova.starter.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "cbs.nova.logging")
public record CbsNovaLoggingProperties(
        @DefaultValue("INFO") Level lifecycle,
        @DefaultValue("INFO") Level http,
        @DefaultValue("true") boolean mdcEnabled) {

  public enum Level {
    DEBUG, INFO, WARN, ERROR, OFF
  }
}
