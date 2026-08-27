package cbs.nova.starter.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "cbs.nova.dry-run")
@Validated
//TODO: redo to a class with lombok's annotations
public record DryRunProperties(
        @DefaultValue Context context,
        @Valid @DefaultValue Log log) {
  public DryRunProperties {
    context = context == null ? new Context("threadlocal") : context;
    log = log == null ? new Log(1000) : log;
  }

  public record Context(@DefaultValue("threadlocal") String type) {
    public Context {
      if (!"threadlocal".equals(type)) {
        throw new IllegalArgumentException(
                "Invalid cbs.nova.dry-run.context.type '" + type
                        + "'; only 'threadlocal' is supported");
      }
    }
  }

  public record Log(
          @DefaultValue("1000") @Min(1) int maxEventsPerRun) {
  }
}
