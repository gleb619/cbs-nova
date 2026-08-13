package cbs.nova.starter.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for preview/explain runtime behavior.
 */
@ConfigurationProperties(prefix = "cbs.nova.preview")
@Validated
public record CbsNovaPreviewProperties(
        @DefaultValue CallTree callTree,
        @Valid @DefaultValue Cache cache) {

  public CbsNovaPreviewProperties {
    callTree = callTree == null ? new CallTree(32) : callTree;
    cache = cache == null ? new Cache(true, 300000) : cache;
  }

  public record CallTree(@DefaultValue("32") int maxDepth) {
  }

  public record Cache(
          @DefaultValue("true") boolean enabled,
          @DefaultValue("300000") @Min(0) long ttlMs) {
  }
}
