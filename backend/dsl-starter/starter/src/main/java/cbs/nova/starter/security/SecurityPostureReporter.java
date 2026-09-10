package cbs.nova.starter.security;

import cbs.nova.starter.config.properties.CbsSecurityOidcProperties;
import cbs.nova.starter.config.properties.CbsSecurityRateLimitProperties;
import cbs.nova.starter.config.properties.DslProperties;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Boot-time security posture report (T413).
 *
 * <p>
 * Emits a single multi-line block summarizing which of the three opt-in guards are active. The
 * block is logged once at startup, after every singleton is wired, so the operator gets a one-line
 * "what is the deploy actually doing" answer without grepping multiple classes.
 *
 * <p>
 * Scope: <strong>production profile only</strong> — see the rationale in
 * {@code application-production.yml} and {@code docs/architecture-backend.md}. Dev / default stays
 * quiet because tests, IDE boot, and dev loops would otherwise spam a posture block on every
 * refresh. The block is at INFO when every guard is on; at WARN if any guard is off in production
 * (i.e. an operator flipped an escape hatch — we want the warning to be loud).
 *
 * <p>
 * The reporter is wired as a {@link SmartInitializingSingleton} lambda bean — mirroring
 * {@code ApiKeyAuthMisconfigurationWarning} so it is reachable from lightweight
 * {@code ApplicationContextRunner} tests that don't invoke {@code ApplicationRunner}, and so the
 * dependency wiring (DSL/OIDC/rate-limit properties, {@link Environment}) is managed by the
 * container rather than by hand.
 *
 * <p>
 * The OIDC issuer URI is intentionally reported as <em>configured / not configured</em> (never
 * echoed). The {@code ProductionSecurityPostureValidator} prints the masked URL — this reporter
 * just prints a boolean.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({DslProperties.class,
    CbsSecurityOidcProperties.class,
    CbsSecurityRateLimitProperties.class})
public class SecurityPostureReporter {

  static final String PRODUCTION_PROFILE = ProductionSecurityPostureValidator.PRODUCTION_PROFILE;
  static final String OIDC_ISSUER_URI_PROPERTY = ProductionSecurityPostureValidator.OIDC_ISSUER_URI_PROPERTY;

  /**
   * Build the {@link SmartInitializingSingleton} that emits the posture block. Captures the
   * property holders and the active {@link Environment} as constructor params so the boot report
   * runs with the final, bound property values — never earlier than
   * {@code afterSingletonsInstantiated()}.
   */
  @Bean(name = "cbsNovaSecurityPostureReporter")
  SmartInitializingSingleton securityPostureReporter(
          DslProperties dslProperties,
          CbsSecurityOidcProperties oidcProperties,
          CbsSecurityRateLimitProperties rateLimitProperties,
          Environment environment) {
    return () -> {
      if (!environmentMatchesProduction(environment)) {
        return;
      }

      boolean authOn = Boolean.TRUE.equals(dslProperties.auth().enabled());
      boolean rateLimitOn = rateLimitProperties.enabled();
      boolean oidcOn = oidcProperties.enabled();
      boolean rbacOn = Boolean.TRUE.equals(dslProperties.auth().rbac().enabled());
      String issuerUri = environment.getProperty(OIDC_ISSUER_URI_PROPERTY, "");
      boolean issuerConfigured = issuerUri != null && !issuerUri.isBlank();

      String block = ""
              + "\n========== cbs-nova security posture (profile: production) ==========\n"
              + "  api-key guard (cbs.dsl.auth.enabled)             : " + authOn + "\n"
              + "  rate-limit guard (cbs.security.ratelimit.enabled): " + rateLimitOn + "\n"
              + "  OIDC resource-server (cbs.security.oidc.enabled): " + oidcOn + "\n"
              + "  OIDC issuer URI configured                       : "
              + issuerConfigured + "\n"
              + "  RBAC (cbs.dsl.auth.rbac.enabled, optional)       : " + rbacOn + "\n"
              + "====================================================================";

      boolean allGuardsOn = authOn && rateLimitOn && oidcOn && issuerConfigured;
      if (allGuardsOn) {
        log.info(block);
      } else {
        // WARN: an operator explicitly turned a guard off under the production profile. The
        // escape hatch is documented in application-production.yml, but we still want a loud
        // one-shot reminder at boot.
        log.warn(block);
      }
    };
  }

  static boolean environmentMatchesProduction(Environment environment) {
    for (String profile : environment.getActiveProfiles()) {
      if (PRODUCTION_PROFILE.equals(profile)) {
        return true;
      }
    }
    return false;
  }
}
