package cbs.nova.starter.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "cbs.nova.dry-run")
@Validated
// Constructor-bound record + @DefaultValue is the chosen config-properties idiom (see
// DslProperties).
public record DryRunProperties(
        @DefaultValue Context context,
        @Valid @DefaultValue Log log) {
  public DryRunProperties {
    context = context == null ? new Context("mdc") : context;
    log = log == null ? new Log(1000) : log;
  }

  public record Context(@DefaultValue("mdc") String type) {
    private static final Set<String> SUPPORTED = Set.of("mdc", "threadlocal");

    public Context {
      if (!SUPPORTED.contains(type)) {
        throw new IllegalArgumentException(
                "Invalid cbs.nova.dry-run.context.type '" + type
                        + "'; supported values: " + SUPPORTED);
      }
    }
  }

  public record Log(
          @DefaultValue("1000") @Min(1) int maxEventsPerRun) {
  }
}
