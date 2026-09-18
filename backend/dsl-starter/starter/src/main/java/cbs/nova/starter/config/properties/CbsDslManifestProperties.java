package cbs.nova.starter.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for the piece-manifest loader ({@code cbs.dsl.manifest.*}).
 *
 * <p>
 * The loader is enabled by default but is fully inert when the configured path is blank or points
 * to a missing resource: existing deployments without a manifest file start cleanly with an empty
 * snapshot.
 */
@ConfigurationProperties(prefix = "cbs.dsl.manifest")
public record CbsDslManifestProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("classpath:piece-manifest.yaml") String path) {

  public CbsDslManifestProperties {
    path = path == null || path.isBlank() ? "classpath:piece-manifest.yaml" : path;
  }
}
