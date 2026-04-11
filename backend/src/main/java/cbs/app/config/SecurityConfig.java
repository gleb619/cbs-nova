package cbs.app.config;

import cbs.app.config.SecurityConfig.LocalAuthProperties;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(LocalAuthProperties.class)
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.requestMatchers(
                "/api/public/**", "/actuator/health",
                "/swagger-ui/**", "/v3/api-docs/**")
            .permitAll()
            .anyRequest()
            .authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)))
        .build();
  }

  @Bean("jwtDecoder")
  @ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
  public JwtDecoder jwtDecoderKeycloak(
      @Value("${keycloak.auth-server-url:http://localhost:9080}") String keycloakServerUrl,
      @Value("${keycloak.realm:cbsnova}") String keycloakRealm) {
    String jwtIssuerUri = keycloakServerUrl + "/realms/" + keycloakRealm;
    return NimbusJwtDecoder.withIssuerLocation(jwtIssuerUri).build();
  }

  /**
   * openssl genrsa -out backend/src/main/resources/local-jwt.pem 2048 openssl rsa -in
   * backend/src/main/resources/local-jwt.pem -pubout -out backend/src/main/resources/local-jwt-public.pem
   */
  @Bean("jwtDecoder")
  @ConditionalOnProperty(name = "keycloak.enabled", havingValue = "false")
  public JwtDecoder jwtDecoderLocal(
      @Value("classpath:local-jwt-public.pem") Resource publicKeyResource) throws Exception {
    String key = new String(publicKeyResource.getInputStream().readAllBytes())
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replaceAll("\\s", "");

    byte[] decoded = Base64.getDecoder().decode(key);
    RSAPublicKey publicKey = (RSAPublicKey)
        KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));

    return NimbusJwtDecoder.withPublicKey(publicKey).build();
  }

  @Bean
  @ConditionalOnProperty(name = "keycloak.enabled", havingValue = "false")
  public UserDetailsService userDetailsService(LocalAuthProperties props) {
    var users = props.users().stream()
        .map(u -> User.withUsername(u.username())
            .password(u.password())
            .roles(u.roles() != null ? u.roles().toArray(String[]::new) : new String[]{"USER"})
            .build())
        .toList();
    return new InMemoryUserDetailsManager(users);
  }

  @Bean
  @ConditionalOnProperty(name = "keycloak.enabled", havingValue = "false")
  public AuthenticationManager authenticationManager(
      UserDetailsService uds, PasswordEncoder encoder) {
    var provider = new DaoAuthenticationProvider(uds);
    provider.setPasswordEncoder(encoder);
    return new ProviderManager(provider);
  }

  @Bean
  @ConditionalOnProperty(name = "keycloak.enabled", havingValue = "false")
  public PasswordEncoder passwordEncoder() {
    return NoOpPasswordEncoder.getInstance();
  }

  @ConfigurationProperties(prefix = "local-auth")
  public record LocalAuthProperties(List<LocalUser> users) {

  }

  public record LocalUser(String username, String password, List<String> roles) {

  }
}
