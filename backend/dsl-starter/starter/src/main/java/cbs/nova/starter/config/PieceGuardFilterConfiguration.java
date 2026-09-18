package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.CbsDslManifestProperties;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.security.FeatureFlagSource;
import cbs.nova.starter.security.PieceGuardFilter;
import cbs.nova.starter.security.PropertiesFeatureFlagSource;
import cbs.nova.starter.security.RoleResolver;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.service.PieceManifestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import tools.jackson.databind.ObjectMapper;

/**
 * Registers {@link PieceGuardFilter} (T549) as a first-class auto-configuration.
 *
 * <p>
 * Activated only when {@link PieceManifestService} is present (i.e.
 * {@code cbs.dsl.manifest.enabled=true}), so the guard vanishes entirely when the manifest
 * subsystem is off — no behaviour change for deployments that do not opt in.
 * {@code after = DslManifestConfiguration.class} guarantees the manifest service bean is defined
 * before this class's {@code @ConditionalOnBean} is evaluated — the phase-ordering lesson from
 * T541/T548: a {@code @ConditionalOnBean} config must be a first-class entry in
 * {@code AutoConfiguration.imports}, never nested via {@code @Import}.
 *
 * <p>
 * Filter order: {@link Ordered#HIGHEST_PRECEDENCE} + 3. The +1 band is
 * {@code ApiKeyAuthFilterConfiguration} (validates {@code X-Api-Key}); the +2 band holds
 * {@code RbacAuthorizationFilter} (resolves the role) and {@code RateLimitFilter} (global
 * per-client-IP limiter). The guard runs after all of them — the principal and role are resolvable
 * when its checks evaluate — and before the route handler.
 */
@Slf4j
@AutoConfiguration(after = DslManifestConfiguration.class)
@ConditionalOnBean(PieceManifestService.class)
public class PieceGuardFilterConfiguration {

  /**
   * Role source for the guard, mirroring {@link RbacFilterConfiguration#rbacRoleResolver}. Only
   * registered when RBAC is off (RBAC's own bean wins via {@code @ConditionalOnMissingBean}).
   * {@link DslProperties} is optional here: minimal test contexts (and apps that never wire the DSL
   * properties) fall back to the default {@code roles} claim instead of failing to boot.
   */
  @Bean
  @ConditionalOnMissingBean
  public RoleResolver pieceGuardRoleResolver(ObjectProvider<DslProperties> propertiesProvider) {
    DslProperties properties = propertiesProvider.getIfAvailable();
    String claim = properties == null ? null : properties.auth().rbac().claim();
    if (claim == null || claim.isBlank()) {
      claim = StarterConstants.DEFAULT_CLAIM_NAME;
    }
    return new RoleResolver(claim);
  }

  @Bean
  @ConditionalOnMissingBean
  public FeatureFlagSource featureFlagSource(CbsDslManifestProperties properties) {
    return new PropertiesFeatureFlagSource(properties);
  }

  @Bean
  @ConditionalOnMissingBean
  public PieceGuardFilter pieceGuardFilter(
          PieceManifestService manifestService,
          RoleResolver roleResolver,
          FeatureFlagSource flagSource,
          CbsDslManifestProperties properties,
          ObjectProvider<DslAuditService> auditServiceProvider,
          ObjectMapper objectMapper) {
    log.info("PieceGuardFilter active — pre-check enforcement on manifest api-target routes");
    return new PieceGuardFilter(manifestService, roleResolver, flagSource, properties,
            auditServiceProvider, objectMapper, System::nanoTime);
  }

  @Bean
  public FilterRegistrationBean<PieceGuardFilter> pieceGuardFilterRegistration(
          PieceGuardFilter filter) {
    FilterRegistrationBean<PieceGuardFilter> registration = new FilterRegistrationBean<>(filter);
    // +1 = ApiKeyAuthFilter, +2 = RbacAuthorizationFilter + RateLimitFilter; run after them so
    // the principal/role are resolved and the global limiter has already accounted the request.
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
    registration.addUrlPatterns("/api/*");
    return registration;
  }
}
