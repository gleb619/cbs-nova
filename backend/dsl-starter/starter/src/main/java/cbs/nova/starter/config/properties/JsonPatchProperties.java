package cbs.nova.starter.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for the JSON Merge Patch helper ({@code cbs.dsl.helper.json-patch}).
 */
@ConfigurationProperties(prefix = "cbs.dsl.helper.json-patch")
public record JsonPatchProperties(
        @DefaultValue("false") Boolean prettyPrint) {

  public JsonPatchProperties {
    prettyPrint = prettyPrint == null ? false : prettyPrint;
  }
}
