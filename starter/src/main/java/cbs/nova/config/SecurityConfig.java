package cbs.nova.config;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Value("${keycloak.auth-server-url:http://localhost:9080}")
  private String keycloakServerUrl;

  @Value("${keycloak.realm:cbsnova}")
  private String keycloakRealm;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.requestMatchers("/api/public/**")
            .permitAll()
            .requestMatchers("/actuator/health")
            .permitAll()
            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**")
            .permitAll()
            .anyRequest()
            .authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)));

    return http.build();
  }

  @Bean("jwtDecoder")
  @ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
  public JwtDecoder jwtDecoderKeycloak() {
    String jwtIssuerUri = keycloakServerUrl + "/realms/" + keycloakRealm;
    return NimbusJwtDecoder.withIssuerLocation(jwtIssuerUri).build();
  }

  /**
   * openssl genrsa -out backend/src/main/resources/local-jwt.pem 2048 openssl rsa -in
   * backend/src/main/resources/local-jwt.pem -pubout -out backend/src/main/resources/local-jwt-public.pem
   */
  @Bean("jwtDecoder")
  @ConditionalOnProperty(name = "keycloak.enabled", havingValue = "false")
  public JwtDecoder jwtDecoderLocal(@Value("classpath:local-jwt-public.pem") Resource publicKeyResource)
      throws Exception {
    String key = new String(publicKeyResource.getInputStream().readAllBytes())
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replaceAll("\\s", "");

    byte[] decoded = Base64.getDecoder().decode(key);
    RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
        .generatePublic(new X509EncodedKeySpec(decoded));

    return NimbusJwtDecoder.withPublicKey(publicKey).build();
  }
}
