package cbs.nova.starter.security;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;
import lombok.RequiredArgsConstructor;

/**
 * Fail-fast guard for the {@code production} Spring profile (T413).
 *
 * <p>
 * Activated by the {@code application-production.yml} profile, which sets the secure defaults (auth
 * on, rate limit on, OIDC resource-server on, issuer URI required). This class enforces the one
 * pre-condition the YAML cannot express by itself: the OIDC issuer URI must be present. If the
 * {@code production} profile is active and
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} is blank, the application context
 * fails to refresh and the JVM exits with a clear message naming the exact property.
 *
 * <p>
 * Implemented as a {@link SmartInitializingSingleton} (mirroring
 * {@code ApiKeyAuthMisconfigurationWarning}) so it is reachable from lightweight
 * {@code ApplicationContextRunner} tests and so it runs after every other singleton is wired — the
 * property source stack is fully populated by then.
 *
 * <p>
 * Only fires under the {@code production} profile. Dev / default / explicit-test profiles are never
 * affected; the OIDC guard itself stays opt-in there (see {@code CbsSecurityOidcProperties}).
 */
@Component
@RequiredArgsConstructor
public class ProductionSecurityPostureValidator implements SmartInitializingSingleton {

  /**
   * Profile that triggers the fail-fast check. Spring resolves active profiles from
   * {@code spring.profiles.active}, env var {@code SPRING_PROFILES_ACTIVE}, and command-line
   * {@code --spring.profiles.active=...}; the check fires whenever this token is in the active set.
   */
  static final String PRODUCTION_PROFILE = "production";

  /**
   * Property that the production profile requires to be non-blank. Matches
   * {@code application-production.yml} (and {@code app/compose/auth.yml}).
   */
  static final String OIDC_ISSUER_URI_PROPERTY = "spring.security.oauth2.resourceserver.jwt.issuer-uri";

  private static final Logger LOG = LoggerFactory
          .getLogger(ProductionSecurityPostureValidator.class);

  private final Environment environment;

  @Override
  public void afterSingletonsInstantiated() {
    String[] activeProfiles = environment.getActiveProfiles();
    boolean productionActive = Arrays.asList(activeProfiles).contains(PRODUCTION_PROFILE);
    if (!productionActive) {
      return;
    }

    String issuerUri = environment.getProperty(OIDC_ISSUER_URI_PROPERTY, "");
    if (issuerUri == null || issuerUri.isBlank()) {
      String message = "Spring profile 'production' requires a non-blank OIDC issuer URI, but "
              + OIDC_ISSUER_URI_PROPERTY + " is unset or empty. Set "
              + OIDC_ISSUER_URI_PROPERTY + " (typically via the OIDC_ISSUER_URI env var) to your "
              + "Keycloak/OIDC realm issuer, e.g. https://idp.example/realms/cbs-nova. Refusing to "
              + "boot a misconfigured production deploy.";
      LOG.error(message);
      throw new IllegalStateException(message);
    }

    LOG.info("production profile active — OIDC issuer URI configured ({})", maskIssuer(issuerUri));
  }

  /**
   * Redact the path component so logs do not leak the realm slug or internal-only paths. The scheme
   * + host are kept so operators can see which IdP the deploy is wired against.
   */
  private static String maskIssuer(String issuerUri) {
    int slashSlash = issuerUri.indexOf("//");
    if (slashSlash < 0) {
      return issuerUri;
    }
    int pathStart = issuerUri.indexOf('/', slashSlash + 2);
    if (pathStart < 0) {
      return issuerUri;
    }
    return issuerUri.substring(0, pathStart) + "/***";
  }
}
