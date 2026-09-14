package cbs.nova.starter.config.properties;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cbs.nova.explain")
public record CbsNovaExplainProperties(
        @DefaultValue("4000") @Min(0) int budgetChars,
        @DefaultValue("explain/") String resourcesPrefix,
        @DefaultValue("128") @Min(0) int nameMaxTokens,
        @DefaultValue("256") @Min(0) int descriptionMaxTokens,
        @DefaultValue("4096") @Min(0) int mermaidMaxTokens) {

  public CbsNovaExplainProperties {
    budgetChars = Math.max(0, budgetChars);
    nameMaxTokens = Math.max(0, nameMaxTokens);
    descriptionMaxTokens = Math.max(0, descriptionMaxTokens);
    mermaidMaxTokens = Math.max(0, mermaidMaxTokens);
    if (resourcesPrefix == null || resourcesPrefix.isBlank()) {
      throw new IllegalArgumentException("cbs.nova.explain.resources-prefix must not be blank");
    }
  }
}
