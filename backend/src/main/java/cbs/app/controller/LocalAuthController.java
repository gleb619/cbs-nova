package cbs.app.controller;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/auth")
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "false")
public class LocalAuthController {

  /**
   * Generates a PKCS#8 formatted RSA private key from the configured resource.
   *
   * <p>openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt \ -in
   * backend/src/main/resources/local-jwt.pem \ -out backend/src/main/resources/local-jwt-pkcs8.pem
   */
  @Value("classpath:local-jwt-pkcs8.pem")
  private final Resource privateKeyResource;

  @Value("${spring.application.name}")
  private final String appName;

  private final AuthenticationManager authenticationManager;

  @PostMapping("/token")
  public ResponseEntity<?> token(@RequestBody LoginRequest request) {
    try {
      log.info("Prepare to create a access token for local user: {}", request);
      Authentication auth = authenticationManager.authenticate(
          new UsernamePasswordAuthenticationToken(request.username(), request.password()));

      long now = System.currentTimeMillis();
      List<String> roles = auth.getAuthorities().stream()
          .filter(a -> Objects.nonNull(a.getAuthority()))
          .map(a -> a.getAuthority().replace("ROLE_", ""))
          .toList();

      // 1 hour
      Date expirationTime = new Date(now + 1000 * 60 * 60);
      JWTClaimsSet claims = new JWTClaimsSet.Builder()
          .subject(auth.getName())
          .issuer(appName)
          .issueTime(new Date(now))
          .expirationTime(expirationTime)
          .claim("preferred_username", auth.getName())
          .claim("realm_access", Map.of("roles", roles))
          .build();

      SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
      jwt.sign(new RSASSASigner(loadPrivateKey()));

      log.info(
          "Created token for for local user: {}, till {}",
          request,
          new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(expirationTime));

      return ResponseEntity.ok(new TokenResponse(jwt.serialize(), "Bearer", 3600L));
    } catch (AuthenticationException e) {
      log.error("Invalid username or password", e);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Map.of("error", "invalid_credentials", "message", "Invalid username or password"));
    } catch (Exception e) {
      log.error("Failed to generate token", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", "token_generation_failed", "message", "Failed to generate token"));
    }
  }

  private RSAPrivateKey loadPrivateKey() throws Exception {
    String key = new String(privateKeyResource.getInputStream().readAllBytes())
        .replaceAll("-{5}.*?-{5}", "")
        .replaceAll("\\s", "");
    return (RSAPrivateKey) KeyFactory.getInstance("RSA")
        .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(key)));
  }

  record LoginRequest(String username, String password) {

    @Override
    public String toString() {
      return username;
    }
  }

  record TokenResponse(String access_token, String token_type, long expires_in) {

    @Override
    public String toString() {
      return "TokenResponse{" + "token_type='"
             + token_type + '\'' + ", expires_in="
             + expires_in + '}';
    }
  }
}
