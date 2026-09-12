package cbs.nova.starter.config.properties;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cbs.nova.explain")
public record CbsNovaExplainProperties(
        @DefaultValue("4000") @Min(0) int budgetChars) {

  public CbsNovaExplainProperties {
    budgetChars = Math.max(0, budgetChars);
  }
}
