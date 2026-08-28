package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.CbsSecurityOidcProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Opt-in OIDC / JWT resource-server guard for the DSL REST API.
 *
 * <p>The starter ships with a no-op, fully-permissive filter chain (default mode,
 * matches the historical behaviour) and an opt-in JWT-protected chain that
 * activates when {@code cbs.security.oidc.enabled=true}. The two chains are
 * mutually exclusive — exactly one {@link SecurityFilterChain} bean is published
 * at runtime, so the default Spring Boot web security configuration backs off
 * (it is {@code @ConditionalOnDefaultWebSecurity}).
 *
 * <h2>Default mode (OIDC disabled)</h2>
 * <ul>
 * <li>Property {@code cbs.security.oidc.enabled} is missing or {@code false}.</li>
 * <li>A single permissive {@link SecurityFilterChain} is registered; every
 * request is allowed, no {@code Authentication} is required.</li>
 * <li>No OIDC-specific beans ({@code JwtDecoder},
 * {@code OAuth2ResourceServerConfigurer}) are wired.</li>
 * </ul>
 *
 * <h2>Secured mode (OIDC enabled)</h2>
 * <ul>
 * <li>Property {@code cbs.security.oidc.enabled=true} activates the
 * {@link #oidcSecurityFilterChain(HttpSecurity)} bean.</li>
 * <li>The configured {@code protectedPaths} (default
 * {@code /api/dsl/**} and {@code /api/executions/**}) require a valid
 * Bearer JWT. {@code WWW-Authenticate: Bearer} is returned on 401.</li>
 * <li>Actuator health, springdoc/OpenAPI and any path in
 * {@code permitAllPaths} stay anonymous.</li>
 * <li>The JWT decoder is auto-configured by Spring Boot from
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} (pointing at
 * the compose Keycloak realm, e.g.
 * {@code http://keycloak:8080/realms/cbs-nova}).</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(CbsSecurityOidcProperties.class)
public class SecurityConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(SecurityConfiguration.class);

  /**
   * Default permissive chain. Always registered (when no OIDC chain is in scope)
   * so that Spring Boot's default-everything-authenticated chain is suppressed.
   * This bean is the only one present in the default (OIDC disabled) mode,
   * making behaviour byte-identical to the pre-OIDC starter: every endpoint
   * remains anonymous, including /actuator/** and /v3/api-docs.
   */
  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE)
  @ConditionalOnProperty(name = "cbs.security.oidc.enabled", havingValue = "false", matchIfMissing = true)
  public SecurityFilterChain permitAllSecurityFilterChain(HttpSecurity http) throws Exception {
    LOG.info("cbs.security.oidc.enabled is false (default) — DSL REST API is unauthenticated");
    return http
            .securityMatcher("/**")
            .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .build();
  }

  /**
   * OIDC-protected chain. Activated when {@code cbs.security.oidc.enabled=true};
   * absent from the context in default mode (per design constraint). The JWT
   * decoder is supplied by Spring Boot's auto-configuration from
   * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}.
   */
  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE)
  @ConditionalOnProperty(name = "cbs.security.oidc.enabled", havingValue = "true")
  public SecurityFilterChain oidcSecurityFilterChain(HttpSecurity http,
                                                     CbsSecurityOidcProperties properties) throws Exception {
    LOG.info("cbs.security.oidc.enabled=true — DSL REST API requires JWT for {}",
            properties.protectedPaths());
    var protectedMatchers = properties.protectedPaths().stream()
            .<RequestMatcher>map(PathPatternRequestMatcher::pathPattern)
            .toList();
    var permitAllMatchers = properties.permitAllPaths().stream()
            .<RequestMatcher>map(PathPatternRequestMatcher::pathPattern)
            .toList();
    return http
            .securityMatcher("/**")
            .authorizeHttpRequests(authorize -> authorize
                    .requestMatchers(toArray(permitAllMatchers)).permitAll()
                    .requestMatchers(toArray(protectedMatchers)).authenticated()
                    .anyRequest().permitAll())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .build();
  }

  private static RequestMatcher[] toArray(List<RequestMatcher> matchers) {
    return matchers.toArray(new RequestMatcher[0]);
  }
}
