package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.DslProperties;
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

/**
 * Opt-in registration of {@link RbacAuthorizationFilter}. The filter is registered ONLY when
 * {@code cbs.dsl.auth.rbac.enabled=true}; the property defaults to {@code false} (see
 * {@link DslProperties.Auth}) so default mode is byte-identical to the pre-T408 starter.
 *
 * <p>
 * <b>Property prefix.</b> This configuration deliberately uses {@code cbs.dsl.auth.rbac} — the
 * existing {@link ApiKeyAuthFilterConfiguration} is gated on {@code cbs.dsl.auth.enabled} and
 * {@link DslProperties} is bound to {@code cbs.dsl.*}. RBAC is a sub-feature of the existing DSL
 * auth surface, so the {@code cbs.dsl.auth.rbac} prefix keeps the auth-related properties in one
 * namespace and re-uses the same {@link DslProperties} record (the plan document suggested
 * {@code dsl.auth.rbac}, but the real codebase uses {@code cbs.dsl.auth.*} and the task asked for
 * consistency with the existing prefix).
 *
 * <p>
 * <b>Filter ordering.</b> The RBAC filter is registered with order {@code HIGHEST_PRECEDENCE + 2},
 * i.e. AFTER the API-key filter (which runs at {@code HIGHEST_PRECEDENCE + 1}). Running RBAC second
 * guarantees an authenticated principal is present in the request when {@link RoleResolver} runs.
 *
 * <p>
 * <b>Interplay with auth.</b> When {@code cbs.dsl.auth.rbac.enabled=true} but no auth filter is
 * active (anonymous mode):
 * <ul>
 * <li>{@code GET /api/**} → allowed as {@link Role#VIEWER}.</li>
 * <li>Any mutating {@code /api/**} → 403 with {@code FORBIDDEN} envelope (fail-closed).</li>
 * </ul>
 * To require authentication AND role enforcement, enable both {@code cbs.dsl.auth.enabled=true}
 * (API key) or {@code cbs.security.oidc.enabled=true} (JWT) AND
 * {@code cbs.dsl.auth.rbac.enabled=true}. The {@link RoleResolver} treats {@code X-Api-Key} headers
 * as {@link Role#ADMIN} and JWT claims (configurable via {@code cbs.dsl.auth.rbac.claim}, default
 * {@code "roles"}) as the source of roles for OIDC principals.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "cbs.dsl.auth.rbac", name = "enabled", havingValue = "true")
public class RbacFilterConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public RoleResolver rbacRoleResolver(DslProperties properties) {
    String claim = properties.auth().rbac().claim();
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
