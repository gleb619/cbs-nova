package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.security.RbacAuthorizationFilter;
import cbs.nova.starter.security.Role;
import cbs.nova.starter.security.RoleResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "cbs.dsl.auth.rbac", name = "enabled", havingValue = "true")
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
          ObjectMapper objectMapper) {
    return new RbacAuthorizationFilter(roleResolver, objectMapper);
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
