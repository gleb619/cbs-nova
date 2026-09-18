package cbs.nova.starter.security;

import cbs.nova.starter.config.properties.CbsDslManifestProperties;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Default {@link FeatureFlagSource}: flags are static booleans bound from
 * {@code cbs.dsl.manifest.flags.<name>}=true in {@link CbsDslManifestProperties}. A flag that is
 * absent (or set to {@code false}) is treated as disabled — fail-closed, matching the rest of the
 * manifest guard. The request argument is ignored; there is no per-principal variance in the
 * properties-backed default.
 */
public final class PropertiesFeatureFlagSource implements FeatureFlagSource {

  private final CbsDslManifestProperties properties;

  public PropertiesFeatureFlagSource(CbsDslManifestProperties properties) {
    this.properties = properties;
  }

  @Override
  public boolean isEnabled(String flag, HttpServletRequest request) {
    if (flag == null || flag.isBlank()) {
      return false;
    }
    return properties.flags().getOrDefault(flag, Boolean.FALSE);
  }
}
