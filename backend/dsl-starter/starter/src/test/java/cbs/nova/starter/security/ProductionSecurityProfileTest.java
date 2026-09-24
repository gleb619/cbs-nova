package cbs.nova.starter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import cbs.nova.starter.config.properties.CbsSecurityOidcProperties;
import cbs.nova.starter.config.properties.CbsSecurityRateLimitProperties;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.service.ApiKeyStore;
import cbs.nova.starter.web.ApiKeyAuthFilter;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies the {@code production} Spring profile (T413). Each {@code @Nested} class boots the
 * starter under {@code @ActiveProfiles("production")} so {@code application-production.yml} is
 * loaded on top of {@code application.yml}.
 *
 * <p>
 * Three behaviours pinned:
 * <ol>
 * <li>Under the profile, the three guards' beans/filters come up (API-key filter, rate-limit
 * filter, OIDC chain).</li>
 * <li>Per-guard escape hatches still win over the profile yml — {@code cbs.dsl.auth.enabled=false}
 * via {@code @TestPropertySource}-equivalent properties excludes the API-key config.</li>
 * <li>Boot fails fast with a clear message when {@code production} is active but
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} is blank.</li>
 * </ol>
 *
 * <p>
 * The posture-report log block is asserted in {@code PostureLogCapture} below; we keep that
 * isolated in its own {@code @Nested} so log capture for the fail-fast message and for the posture
 * block don't interfere.
 */
class ProductionSecurityProfileTest {

  /**
   * Bare {@code @SpringBootApplication} that scans the starter package and provides a mocked
   * {@link JwtDecoder} so the {@code oidcSecurityFilterChain} bean can wire without trying to fetch
   * JWKs from the (fake) issuer URI. Mirrors the pattern used by
   * {@code SecurityConfigurationTest.GuardEnabledTestApp}.
   */
  @SpringBootApplication(scanBasePackages = "cbs.nova.starter")
  @Import(MockJwtDecoderConfig.class)
  static class TestApp {
    public static void main(String[] args) {
      SpringApplication.run(TestApp.class, args);
    }
  }

  @TestConfiguration
  static class MockJwtDecoderConfig {

    // Same bean name as SecurityConfigurationTest.MockJwtDecoderConfig.jwtDecoder — with
    // spring.main.allow-bean-definition-overriding=true (set in @TestPropertySource) our @Primary
    // mock replaces the scanned one. We deliberately keep the same name so the OIDC chain bean
    // can resolve exactly one JwtDecoder primary in our test context. In every other test (where
    // overriding is NOT enabled) our mock is invisible because it is an inner class of a test
    // class that is only reached via component scan when the host test class is the bootstrap —
    // Spring's default disable-bean-override policy keeps the original mock intact.
    @Bean(name = "jwtDecoder")
    @Primary
    JwtDecoder productionProfileJwtDecoder() {
      return mock(JwtDecoder.class);
    }
  }

  @Nested
  @SpringBootTest(classes = TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
  @ActiveProfiles("production")
  @TestPropertySource(properties = {
      // Dummy issuer so ProductionSecurityPostureValidator passes — the value is never
      // dereferenced because the OIDC chain does not run without a real web container.
      "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://idp.example/realms/cbs-nova",
      "cbs.dsl.worker.enabled=false"
  })
  class ProductionProfileBootsAllGuards {

    @Autowired
    private ApplicationContext context;

    @Test
    void allThreeGuardBeansAndFiltersAreRegistered() {
      // Rate-limit filter bean: always present, but its behaviour is gated by the property.
      // We check the property was bound to true (production yml sets it).
      var rateLimitProps = context.getBean(
              CbsSecurityRateLimitProperties.class);
      assertThat(rateLimitProps.enabled())
              .as("cbs.security.ratelimit.enabled must be true under the production profile")
              .isTrue();

      // API-key filter (gated by cbs.dsl.auth.enabled, true via profile yml).
      assertThat(context.getBeansOfType(ApiKeyAuthFilter.class))
              .as("production profile must publish the ApiKeyAuthFilter bean")
              .isNotEmpty();

      // OIDC resource-server chain bean must be the OIDC one, not the permissive one.
      Map<String, SecurityFilterChain> chains = context.getBeansOfType(SecurityFilterChain.class);
      assertThat(chains)
              .as("production profile must register the oidcSecurityFilterChain bean")
              .containsKey("oidcSecurityFilterChain");
      assertThat(chains.keySet())
              .as("production profile must NOT register the permissive fallback chain")
              .doesNotContain("permitAllSecurityFilterChain");
    }

    @Test
    void postureReporterIsPresent() {
      // The posture reporter is always wired (the @Configuration enables the relevant property
      // holders); in production its SmartInitializingSingleton bean fires. Assert both beans
      // exist so we know the wiring works end-to-end.
      assertThat(context.getBeansOfType(SecurityPostureReporter.class))
              .as("SecurityPostureReporter @Configuration must be wired")
              .isNotEmpty();
      assertThat(context.getBean("cbsNovaSecurityPostureReporter"))
              .as("SmartInitializingSingleton lambda bean must be registered")
              .isInstanceOf(SmartInitializingSingleton.class);
      assertThat(context.getBeansOfType(ProductionSecurityPostureValidator.class))
              .as("ProductionSecurityPostureValidator bean must be wired")
              .isNotEmpty();
    }
  }

  @Nested
  @SpringBootTest(classes = TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
  @ActiveProfiles("production")
  @TestPropertySource(properties = {
      "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://idp.example/realms/cbs-nova",
      "cbs.dsl.worker.enabled=false",
      // Documented escape hatch: properties outrank the profile yml. Pin the contract.
      "cbs.dsl.auth.enabled=false"
  })
  class ExplicitOffWinsOverProfile {

    @Autowired
    private ApplicationContext context;

    @Test
    void apiKeyAuthFilterBeanIsAbsentWhenExplicitlyDisabled() {
      assertThat(context.getBeansOfType(ApiKeyAuthFilter.class))
              .as("cbs.dsl.auth.enabled=false via @TestPropertySource must suppress the API-key filter")
              .isEmpty();
    }
  }

  @Nested
  /**
   * Lightweight, direct assertion of the fail-fast exception message. Uses
   * {@code ApplicationContextRunner} so we can capture the thrown exception text precisely without
   * relying on Spring Boot's testing harness to surface it.
   */
  class FailFastMessageAssertion {

    @Test
    void blankIssuerUnderProductionProfileRaisesIllegalStateException() {
      // The validator is wired by itself — no need for the full SecurityConfiguration here,
      // since the fail-fast is purely a property-source check that fires before any bean that
      // would need an HttpSecurity / JwtDecoder is touched.
      var runner = new WebApplicationContextRunner()
              .withUserConfiguration(
                      ProductionSecurityPostureValidator.class,
                      LightweightTestConfig.class);
      runner.withPropertyValues(
              "spring.profiles.active=production",
              "spring.security.oauth2.resourceserver.jwt.issuer-uri=")
              .run(ctx -> {
                assertThat(ctx).hasFailed();
                assertThat(ctx).getFailure()
                        .hasMessageContaining(
                                "spring.security.oauth2.resourceserver.jwt.issuer-uri")
                        .hasMessageContaining("OIDC_ISSUER_URI");
              });
    }
  }

  /**
   * Posture-report log capture. Production profile + all guards on → INFO block. Production profile
   * + a guard off → WARN block.
   */
  @Nested
  class PostureLogCapture {

    private Logger reporterLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
      reporterLogger = (Logger) LoggerFactory.getLogger(SecurityPostureReporter.class);
      appender = new ListAppender<>();
      appender.start();
      reporterLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
      if (reporterLogger != null && appender != null) {
        reporterLogger.detachAppender(appender);
        appender.stop();
      }
    }

    @Test
    void postureBlockIsEmittedAtInfoWhenAllGuardsOn() {
      var runner = new WebApplicationContextRunner()
              .withUserConfiguration(
                      SecurityPostureReporter.class,
                      LightweightTestConfig.class)
              .withPropertyValues(
                      "spring.profiles.active=production",
                      "cbs.dsl.auth.enabled=true",
                      "cbs.security.ratelimit.enabled=true",
                      "cbs.security.oidc.enabled=true",
                      "cbs.dsl.auth.rbac.enabled=false",
                      "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://idp.example/realms/cbs-nova");

      appender.list.clear();
      runner.run(ctx -> {
        // afterSingletonsInstantiated was already fired during context refresh; check what was
        // captured. Spring's preInstantiateSingletons invokes SmartInitializingSingleton methods
        // for every such bean registered via withUserConfiguration.
        List<ILoggingEvent> postureEvents = appender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("cbs-nova security posture"))
                .toList();
        assertThat(postureEvents)
                .as("production profile + all guards on emits the posture block at INFO")
                .hasSize(1)
                .first()
                .satisfies(event -> {
                  assertThat(event.getLevel()).isEqualTo(Level.INFO);
                  String body = event.getFormattedMessage();
                  assertThat(body)
                          .contains("api-key guard")
                          .contains("rate-limit guard")
                          .contains("OIDC resource-server")
                          .contains("OIDC issuer URI configured")
                          .contains("RBAC");
                });
      });
    }

    @Test
    void postureBlockIsEmittedAtWarnWhenGuardOffInProduction() {
      var runner = new WebApplicationContextRunner()
              .withUserConfiguration(
                      SecurityPostureReporter.class,
                      LightweightTestConfig.class)
              .withPropertyValues(
                      "spring.profiles.active=production",
                      "cbs.dsl.auth.enabled=false",
                      "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://idp.example/realms/cbs-nova");

      appender.list.clear();
      runner.run(ctx -> {
        List<ILoggingEvent> warnEvents = appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .toList();
        assertThat(warnEvents)
                .as("with a guard explicitly off in production, posture block must be WARN")
                .anyMatch(e -> e.getFormattedMessage().contains("cbs-nova security posture"));
      });
    }
  }

  /**
   * Shared collaborators for the lightweight tests. Mirrors the helpers in
   * {@link cbs.nova.starter.config.ApiKeyAuthFilterConfigurationTest}.
   */
  @TestConfiguration
  @EnableConfigurationProperties({
      DslProperties.class,
      CbsSecurityOidcProperties.class,
      CbsSecurityRateLimitProperties.class})
  static class LightweightTestConfig {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    ObjectProvider<ApiKeyStore> apiKeyStoreProvider() {
      return new ObjectProvider<>() {
        @Override
        public ApiKeyStore getIfAvailable() {
          return null;
        }

        @Override
        public ApiKeyStore getIfUnique() {
          return null;
        }

        @Override
        public ApiKeyStore getObject() {
          throw new IllegalStateException("no ApiKeyStore bean in this test context");
        }
      };
    }

    @Bean
    @Primary
    JwtDecoder jwtDecoder() {
      return mock(JwtDecoder.class);
    }
  }
}
