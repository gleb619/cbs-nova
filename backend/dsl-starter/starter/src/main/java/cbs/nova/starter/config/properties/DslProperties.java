package cbs.nova.starter.config.properties;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@ConfigurationProperties(prefix = "dsl")
public class DslProperties {

  private String sourceDir;
  private String taskQueue = "dsl-task-queue";
  private Worker worker = new Worker();
  private Reload reload = new Reload();
  private Auth auth = new Auth();

  @Data
  public static class Worker {

    private boolean enabled;

  }

  @Data
  public static class Reload {

    private boolean enabled;

  }

  @Data
  public static class Auth {

    private String apiKey;

  }
}
