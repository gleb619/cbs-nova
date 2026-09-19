package cbs.nova.starter.config;

import cbs.nova.dsl.config.DslConfig;
import cbs.nova.starter.config.properties.CbsDslManifestProperties;
import cbs.nova.starter.security.ManifestObjectGuard;
import cbs.nova.starter.security.ManifestObjectGuardAdapter;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.service.DslCapabilityRegistry;
import cbs.nova.starter.service.PieceManifestService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers the T552 manifest-based object guard as a first-class auto-configuration.
 *
 * <p>
 * Follows the T541/T548 lesson (same pattern as {@link PieceGuardFilterConfiguration} and
 * {@link PieceCheckPipelineConfiguration}): activated only when {@link PieceManifestService} is
 * present, after {@link DslManifestConfiguration} has wired the loader.
 *
 * <p>
 * The guard ships disabled by default ({@code cbs.dsl.manifest.object-enforcement.enabled=false}):
 * when the feature flag is off the bean is still created but every check returns "allow" so helper
 * dispatch is byte-for-byte identical to pre-T552 behavior.
 */
@AutoConfiguration(after = DslManifestConfiguration.class)
@ConditionalOnBean(PieceManifestService.class)
public class ManifestObjectGuardConfiguration {

  @Bean
  @ConditionalOnMissingBean(DslCapabilityRegistry.class)
  public DslCapabilityRegistry dslCapabilityRegistry() {
    return definitionName -> cbs.nova.starter.model.CapabilityDeclaration.empty();
  }

  @Bean
  @ConditionalOnMissingBean
  public ManifestObjectGuard manifestObjectGuard(
          CbsDslManifestProperties properties,
          PieceManifestService pieceManifestService,
          ObjectProvider<DslAuditService> auditServiceProvider,
          ObjectProvider<MeterRegistry> meterRegistryProvider,
          ObjectProvider<DslCapabilityRegistry> capabilityRegistryProvider) {
    return new ManifestObjectGuard(properties, pieceManifestService, auditServiceProvider,
            meterRegistryProvider, capabilityRegistryProvider);
  }

  /**
   * Registers the manifest guard into the DSL platform runtime so generated Temporal workflows and
   * any direct helper invocations consult the same allowlist as preview pipes.
   */
  @Bean
  public ManifestObjectGuardAdapter manifestObjectGuardAdapter(
          ManifestObjectGuard manifestObjectGuard) {
    ManifestObjectGuardAdapter adapter = new ManifestObjectGuardAdapter(manifestObjectGuard);
    DslConfig.dslConfig().objectGuard().replace(adapter);
    return adapter;
  }
}
