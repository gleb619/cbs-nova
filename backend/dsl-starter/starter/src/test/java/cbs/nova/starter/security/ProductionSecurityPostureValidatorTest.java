package cbs.nova.starter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Direct unit specs for {@link ProductionSecurityPostureValidator} (T738). Pins the production
 * profile's fail-fast guard for the OIDC issuer URI property.
 *
 * <p>
 * {@link ProductionSecurityProfileTest} already exercises this code path indirectly through a
 * {@code WebApplicationContextRunner}; these tests pin the validator's logic without a Spring
 * context so the contract stays visible at the unit tier.
 */
class ProductionSecurityPostureValidatorTest {

  private static final String OIDC_KEY = "spring.security.oauth2.resourceserver.jwt.issuer-uri";

  @Test
  void productionProfileWithBlankIssuerThrows() {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles("production");
    env.setProperty(OIDC_KEY, "");

    ProductionSecurityPostureValidator validator = new ProductionSecurityPostureValidator(env);

    assertThatThrownBy(validator::afterSingletonsInstantiated)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("production")
            .hasMessageContaining(OIDC_KEY)
            .hasMessageContaining("OIDC_ISSUER_URI");
  }

  @Test
  void productionProfileWithUnsetIssuerThrows() {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles("production");
    // Property deliberately absent.

    ProductionSecurityPostureValidator validator = new ProductionSecurityPostureValidator(env);

    assertThatThrownBy(validator::afterSingletonsInstantiated)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(OIDC_KEY);
  }

  @Test
  void productionProfileWithIssuerReturnsNormally() {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles("production");
    env.setProperty(OIDC_KEY, "https://idp.example/realms/cbs-nova");

    ProductionSecurityPostureValidator validator = new ProductionSecurityPostureValidator(env);

    assertThatCode(validator::afterSingletonsInstantiated).doesNotThrowAnyException();
  }

  @Test
  void nonProductionProfileIgnoresBlankIssuer() {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles("dev");
    env.setProperty(OIDC_KEY, "");

    ProductionSecurityPostureValidator validator = new ProductionSecurityPostureValidator(env);

    assertThatCode(validator::afterSingletonsInstantiated).doesNotThrowAnyException();
  }

  @Test
  void noActiveProfilesSkipsValidation() {
    MockEnvironment env = new MockEnvironment();
    ProductionSecurityPostureValidator validator = new ProductionSecurityPostureValidator(env);

    assertThatCode(validator::afterSingletonsInstantiated).doesNotThrowAnyException();
  }

  @Test
  void productionProfileAmongOthersStillTriggersGuard() {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles("dev", "production", "metrics");
    env.setProperty(OIDC_KEY, "");

    ProductionSecurityPostureValidator validator = new ProductionSecurityPostureValidator(env);

    assertThatThrownBy(validator::afterSingletonsInstantiated)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(OIDC_KEY);
  }

  @Test
  void errorMessageMentionsOidcIssuerUriEnvVar() {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles("production");
    env.setProperty(OIDC_KEY, "");

    ProductionSecurityPostureValidator validator = new ProductionSecurityPostureValidator(env);

    assertThatThrownBy(validator::afterSingletonsInstantiated)
            .isInstanceOf(IllegalStateException.class)
            // The message tells operators which env var to set.
            .hasMessageContaining("OIDC_ISSUER_URI");
  }
}
