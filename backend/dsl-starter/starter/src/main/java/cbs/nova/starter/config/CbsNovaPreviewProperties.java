package cbs.nova.starter.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "cbs.nova.preview")
@Validated
public record CbsNovaPreviewProperties(
        @DefaultValue CallTree callTree,
        @Valid @DefaultValue Cache cache,
        @Valid @DefaultValue Execution execution) {

  public CbsNovaPreviewProperties {
    callTree = callTree == null ? new CallTree(32) : callTree;
    cache = cache == null ? new Cache(true, 300000) : cache;
    execution = execution == null ? new Execution(20000, 4) : execution;
  }

  public record CallTree(@DefaultValue("32") int maxDepth) {
  }

  public record Cache(
          @DefaultValue("true") boolean enabled,
          @DefaultValue("300000") @Min(0) long ttlMs) {
  }

  public record Execution(
          @DefaultValue("20000") @Min(0) long timeoutMs,
          @DefaultValue("4") @Min(1) int poolSize) {
  }
}
