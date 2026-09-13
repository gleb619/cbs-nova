package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.CbsSecurityOidcProperties;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(CbsSecurityOidcProperties.class)
public class SecurityConfiguration {

  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE)
  @ConditionalOnProperty(name = "cbs.security.oidc.enabled", havingValue = "false", matchIfMissing = true)
  public SecurityFilterChain permitAllSecurityFilterChain(HttpSecurity http) throws Exception {
    log.info("cbs.security.oidc.enabled is false (default) — DSL REST API is unauthenticated");
    return http
            .securityMatcher("/**")
            .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .build();
  }

  /**
   * OIDC-protected chain. Activated when {@code cbs.security.oidc.enabled=true}; absent from the
   * context in default mode (per design constraint). The JWT decoder is supplied by Spring Boot's
   * auto-configuration from {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}.
   */
  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE)
  @ConditionalOnProperty(name = "cbs.security.oidc.enabled", havingValue = "true")
  public SecurityFilterChain oidcSecurityFilterChain(HttpSecurity http,
          CbsSecurityOidcProperties properties) throws Exception {
    log.info("cbs.security.oidc.enabled=true — DSL REST API requires JWT for {}",
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
