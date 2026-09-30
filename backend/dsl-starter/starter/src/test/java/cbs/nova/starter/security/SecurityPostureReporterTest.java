package cbs.nova.starter.security;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cbs.nova.starter.config.properties.CbsSecurityOidcProperties;
import cbs.nova.starter.config.properties.CbsSecurityRateLimitProperties;
import cbs.nova.starter.config.properties.DslProperties;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Direct unit specs for the {@link SecurityPostureReporter} boot-time log block (T738). Verifies
 * which signals land in the report (api-key / rate-limit / OIDC / issuer URI / RBAC) and which log
 * level the block uses depending on whether every guard is on.
 */
class SecurityPostureReporterTest {

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
  void productionProfileWithAllGuardsOnEmitsInfoBlock() {
    WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(SecurityPostureReporter.class, LightweightConfig.class)
            .withPropertyValues(
                    "spring.profiles.active=production",
                    "cbs.dsl.auth.enabled=true",
                    "cbs.security.ratelimit.enabled=true",
                    "cbs.security.oidc.enabled=true",
                    "cbs.dsl.auth.rbac.enabled=true",
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://idp.example/realms/cbs-nova");

    appender.list.clear();
    runner.run(ctx -> {
      assertThat(ctx).hasNotFailed();
      assertThat(ctx).getBean("cbsNovaSecurityPostureReporter")
              .isInstanceOf(SmartInitializingSingleton.class);
      List<ILoggingEvent> events = appender.list.stream()
              .filter(e -> e.getFormattedMessage().contains("cbs-nova security posture"))
              .toList();
      assertThat(events)
              .as("all guards on in production → exactly one INFO posture block")
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
                        .contains("RBAC")
                        .contains("true");
              });
    });
  }

  @Test
  void productionProfileWithGuardOffEmitsWarnBlock() {
    WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(SecurityPostureReporter.class, LightweightConfig.class)
            .withPropertyValues(
                    "spring.profiles.active=production",
                    "cbs.dsl.auth.enabled=false",
                    "cbs.security.ratelimit.enabled=true",
                    "cbs.security.oidc.enabled=true",
                    "cbs.dsl.auth.rbac.enabled=false",
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://idp.example/realms/cbs-nova");

    appender.list.clear();
    runner.run(ctx -> {
      assertThat(ctx).hasNotFailed();
      List<ILoggingEvent> posture = appender.list.stream()
              .filter(e -> e.getFormattedMessage().contains("cbs-nova security posture"))
              .toList();
      assertThat(posture)
              .as("guard off in production → posture block at WARN")
              .hasSize(1)
              .first()
              .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
      String body = posture.get(0).getFormattedMessage();
      // The off-flag is rendered as the literal string "false".
      assertThat(body).contains("false");
    });
  }

  @Test
  void productionProfileWithBlankIssuerEmitsWarnBlock() {
    WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(SecurityPostureReporter.class, LightweightConfig.class)
            .withPropertyValues(
                    "spring.profiles.active=production",
                    "cbs.dsl.auth.enabled=true",
                    "cbs.security.ratelimit.enabled=true",
                    "cbs.security.oidc.enabled=true",
                    // No issuer URI → allGuardsOn is false → WARN.
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri=");

    appender.list.clear();
    runner.run(ctx -> {
      assertThat(ctx).hasNotFailed();
      List<ILoggingEvent> posture = appender.list.stream()
              .filter(e -> e.getFormattedMessage().contains("cbs-nova security posture"))
              .toList();
      assertThat(posture)
              .as("blank issuer URI → WARN even if boolean flags are on")
              .hasSize(1)
              .first()
              .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
    });
  }

  @Test
  void nonProductionProfileEmitsNoPostureBlock() {
    WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(SecurityPostureReporter.class, LightweightConfig.class)
            .withPropertyValues(
                    "spring.profiles.active=dev",
                    "cbs.dsl.auth.enabled=true",
                    "cbs.security.ratelimit.enabled=true",
                    "cbs.security.oidc.enabled=true");

    appender.list.clear();
    runner.run(ctx -> {
      assertThat(ctx).hasNotFailed();
      long count = appender.list.stream()
              .filter(e -> e.getFormattedMessage().contains("cbs-nova security posture"))
              .count();
      assertThat(count)
              .as("dev profile must NOT emit the production posture block")
              .isZero();
    });
  }

  @Test
  void rbacFalseIsRenderedInTheBlock() {
    WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(SecurityPostureReporter.class, LightweightConfig.class)
            .withPropertyValues(
                    "spring.profiles.active=production",
                    "cbs.dsl.auth.enabled=true",
                    "cbs.security.ratelimit.enabled=true",
                    "cbs.security.oidc.enabled=true",
                    "cbs.dsl.auth.rbac.enabled=false",
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://idp.example/realms/cbs-nova");

    appender.list.clear();
    runner.run(ctx -> {
      assertThat(ctx).hasNotFailed();
      List<ILoggingEvent> posture = appender.list.stream()
              .filter(e -> e.getFormattedMessage().contains("cbs-nova security posture"))
              .toList();
      assertThat(posture).hasSize(1);
      String body = posture.get(0).getFormattedMessage();
      assertThat(body)
              .contains("RBAC")
              .contains("false");
    });
  }

  /**
   * Minimal collaborators: bind the three property holders so the report bean can resolve them.
   */
  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties({
      DslProperties.class,
      CbsSecurityOidcProperties.class,
      CbsSecurityRateLimitProperties.class})
  static class LightweightConfig {

    @Bean
    Boolean rbacOn() {
      // Marker bean to keep the config class from being optimised away.
      return Boolean.TRUE;
    }
  }
}
