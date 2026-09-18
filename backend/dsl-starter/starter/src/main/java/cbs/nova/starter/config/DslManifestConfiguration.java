package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.CbsDslManifestProperties;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.service.PieceCheckBlockRegistry;
import cbs.nova.starter.service.PieceManifestService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

/**
 * Registers the piece-manifest loader as a Spring bean. Conditional on
 * {@code cbs.dsl.manifest.enabled=true} (the default), but the service stays inert when no manifest
 * is configured so existing deployments are unaffected.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "cbs.dsl.manifest", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CbsDslManifestProperties.class)
public class DslManifestConfiguration {

  @Bean
  PieceManifestService pieceManifestService(CbsDslManifestProperties properties,
          ResourceLoader resourceLoader,
          ObjectProvider<DslAuditService> auditServiceProvider,
          ObjectProvider<PieceCheckBlockRegistry> blockRegistryProvider) {
    return new PieceManifestService(properties, resourceLoader, auditServiceProvider,
            blockRegistryProvider);
  }
}
