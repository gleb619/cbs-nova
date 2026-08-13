package cbs.nova.starter.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "dsl")
public record DslProperties(
        String sourceDir,
        @DefaultValue("dsl-task-queue") String taskQueue,
        @DefaultValue Worker worker,
        @DefaultValue Reload reload) {
  public DslProperties {
    worker = worker == null ? new Worker(false) : worker;
    reload = reload == null ? new Reload(true) : reload;
  }

  public record Worker(@DefaultValue("false") boolean enabled) {
  }

  public record Reload(@DefaultValue("true") boolean enabled) {
  }
}
