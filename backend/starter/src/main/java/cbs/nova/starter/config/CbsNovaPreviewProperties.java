package cbs.nova.starter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for preview/explain runtime behavior.
 */
@ConfigurationProperties(prefix = "cbs.nova.preview")
public record CbsNovaPreviewProperties(
        @DefaultValue CallTree callTree,
        @DefaultValue Cache cache) {

  public CbsNovaPreviewProperties {
    callTree = callTree == null ? new CallTree(32) : callTree;
    cache = cache == null ? new Cache(true) : cache;
  }

  public record CallTree(@DefaultValue("32") int maxDepth) {
  }

  public record Cache(@DefaultValue("true") boolean enabled) {
  }
}
