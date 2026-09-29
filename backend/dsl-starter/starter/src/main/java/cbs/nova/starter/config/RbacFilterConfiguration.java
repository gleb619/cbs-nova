package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.security.RbacAuthorizationFilter;
import cbs.nova.starter.security.RoleResolver;
import cbs.nova.starter.service.PieceManifestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import tools.jackson.databind.ObjectMapper;

/**
 * Registers the {@link RbacAuthorizationFilter} as an auto-configuration so it can depend on the
 * manifest-service bean created by {@link DslManifestConfiguration} (T728).
 */
@Slf4j
@AutoConfiguration(after = DslManifestConfiguration.class)
@ConditionalOnProperty(prefix = "cbs.dsl.auth.rbac", name = "enabled", havingValue = "true")
@ConditionalOnBean(PieceManifestService.class)
public class RbacFilterConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public RoleResolver rbacRoleResolver(DslProperties properties) {
    String claim = properties.auth().rbac().claim();
    if (claim == null || claim.isBlank()) {
      claim = StarterConstants.DEFAULT_CLAIM_NAME;
    }
    log.info("cbs.dsl.auth.rbac.enabled=true — RBAC filter active; claim={}", claim);
    return new RoleResolver(claim);
  }

  @Bean
  @ConditionalOnMissingBean
  public RbacAuthorizationFilter rbacAuthorizationFilter(
          RoleResolver roleResolver,
          ObjectMapper objectMapper,
          PieceManifestService manifestService) {
    return new RbacAuthorizationFilter(roleResolver, objectMapper, manifestService);
  }

  @Bean
  public FilterRegistrationBean<RbacAuthorizationFilter> rbacAuthorizationFilterRegistration(
          RbacAuthorizationFilter filter) {
    FilterRegistrationBean<RbacAuthorizationFilter> registration = new FilterRegistrationBean<>(
            filter);
    // Ordered.HIGHEST_PRECEDENCE + 1 is taken by ApiKeyAuthFilterConfiguration; place RBAC just
    // after so the API-key header has been validated (or rejected) before RBAC inspects it.
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
    registration.addUrlPatterns("/api/*");
    return registration;
  }
}
