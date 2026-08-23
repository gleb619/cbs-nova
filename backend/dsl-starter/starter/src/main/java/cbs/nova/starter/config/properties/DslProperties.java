package cbs.nova.starter.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "dsl")
public record DslProperties(
        String sourceDir,
        @DefaultValue("dsl-task-queue") String taskQueue,
        @DefaultValue Worker worker,
        @DefaultValue Reload reload,
        @DefaultValue Auth auth) {
  public DslProperties {
    worker = worker == null ? new Worker(false) : worker;
    reload = reload == null ? new Reload(true) : reload;
    auth = auth == null ? new Auth(null) : auth;
  }

  public record Worker(@DefaultValue("false") boolean enabled) {
  }

  public record Reload(@DefaultValue("true") boolean enabled) {
  }

  public record Auth(String apiKey) {
  }

  public DslProperties(String sourceDir, String taskQueue, Worker worker, Reload reload) {
    this(sourceDir, taskQueue, worker, reload, new Auth(null));
  }
}
