package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.web.ApiKeyAuthFilter;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies the opt-in contract of {@link ApiKeyAuthFilterConfiguration}: the filter beans are
 * created only when {@code cbs.dsl.auth.enabled=true}; otherwise the configuration is excluded and
 * no filter is registered against {@code /api/*}.
 *
 * <p>
 * Also verifies the silent-auth-disablement WARN from {@link ApiKeyAuthMisconfigurationWarning}
 * fires when an operator configures a non-blank {@code cbs.dsl.auth.api-key} but leaves
 * {@code cbs.dsl.auth.enabled=false}.
 */
class ApiKeyAuthFilterConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withUserConfiguration(DslPropertiesConfiguration.class,
                  ApiKeyAuthFilterConfiguration.class,
                  ApiKeyAuthMisconfigurationWarning.class);

  @Test
  void filterBeansAreAbsentWhenAuthEnabledPropertyIsUnset() {
    runner.run(ctx -> {
      assertThat(ctx).doesNotHaveBean(ApiKeyAuthFilter.class);
      assertThat(ctx).doesNotHaveBean(FilterRegistrationBean.class);
    });
  }

  @Test
  void filterBeansAreAbsentWhenAuthEnabledPropertyIsFalse() {
    runner.withPropertyValues("cbs.dsl.auth.enabled=false")
            .run(ctx -> {
              assertThat(ctx).doesNotHaveBean(ApiKeyAuthFilter.class);
              assertThat(ctx).doesNotHaveBean(FilterRegistrationBean.class);
            });
  }

  @Test
  void filterBeansAreCreatedWhenAuthEnabledPropertyIsTrue() {
    runner.withPropertyValues("cbs.dsl.auth.enabled=true")
            .run(ctx -> {
              assertThat(ctx).hasSingleBean(ApiKeyAuthFilter.class);
              assertThat(ctx).hasSingleBean(FilterRegistrationBean.class);
            });
  }

  @Test
  void filterPassesThroughWhenEnabledButApiKeyIsBlank() throws ServletException {
    runner.withPropertyValues("cbs.dsl.auth.enabled=true")
            .run(ctx -> {
              ApiKeyAuthFilter filter = ctx.getBean(ApiKeyAuthFilter.class);
              MockHttpServletRequest request = new MockHttpServletRequest();
              MockHttpServletResponse response = new MockHttpServletResponse();
              boolean[] chainInvoked = {false};
              FilterChain chain = (ServletRequest req,
                      ServletResponse res) -> chainInvoked[0] = true;

              filter.doFilter(request, response, chain);

              assertThat(chainInvoked[0]).as("chain must be invoked when no api-key is configured")
                      .isTrue();
              assertThat(response.getStatus()).isNotEqualTo(401);
            });
  }

  @Test
  void filterBeansCreatedAndFilterEnforcesWhenEnabledAndApiKeySet() throws ServletException {
    runner.withPropertyValues("cbs.dsl.auth.enabled=true", "cbs.dsl.auth.api-key=secret-key")
            .run(ctx -> {
              ApiKeyAuthFilter filter = ctx.getBean(ApiKeyAuthFilter.class);
              MockHttpServletRequest request = new MockHttpServletRequest();
              MockHttpServletResponse response = new MockHttpServletResponse();

              filter.doFilter(request, response, failingChain());

              assertThat(response.getStatus())
                      .as("missing X-Api-Key header must produce 401")
                      .isEqualTo(401);
            });
  }

  // --- WARN-on-misconfiguration tests -----------------------------------------

  private Logger warningLogger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachAppender() {
    warningLogger = (Logger) LoggerFactory.getLogger(ApiKeyAuthMisconfigurationWarning.class);
    appender = new ListAppender<>();
    appender.start();
    warningLogger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    if (warningLogger != null && appender != null) {
      warningLogger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void warnFiresWhenApiKeySetButAuthDisabled() {
    appender.list.clear();
    runner.withPropertyValues("cbs.dsl.auth.enabled=false", "cbs.dsl.auth.api-key=secret-key")
            .run(ctx -> triggerWarningCheck(ctx));
    assertThat(appender.list)
            .as("api-key configured but enabled=false must emit a WARN")
            .anySatisfy(event -> {
              assertThat(event.getLevel()).isEqualTo(Level.WARN);
              assertThat(event.getFormattedMessage())
                      .contains("cbs.dsl.auth.api-key")
                      .contains("cbs.dsl.auth.enabled")
                      .contains("false");
            });
  }

  @Test
  void warnDoesNotFireWhenAuthEnabled() {
    appender.list.clear();
    runner.withPropertyValues("cbs.dsl.auth.enabled=true", "cbs.dsl.auth.api-key=secret-key")
            .run(ctx -> triggerWarningCheck(ctx));
    assertThat(appender.list)
            .as("warn must not fire when the filter is actually active")
            .isEmpty();
  }

  @Test
  void warnDoesNotFireWhenApiKeyIsBlank() {
    appender.list.clear();
    runner.withPropertyValues("cbs.dsl.auth.enabled=false")
            .run(ctx -> triggerWarningCheck(ctx));
    assertThat(appender.list)
            .as("warn must not fire when no api-key is configured")
            .isEmpty();
  }

  private static void triggerWarningCheck(
          org.springframework.context.ConfigurableApplicationContext ctx) {
    org.springframework.beans.factory.SmartInitializingSingleton check = ctx.getBean(
            "apiKeyAuthMisconfigurationCheck",
            org.springframework.beans.factory.SmartInitializingSingleton.class);
    check.afterSingletonsInstantiated();
  }

  private static FilterChain failingChain() {
    return new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response) {
        throw new AssertionError("chain must not be invoked when X-Api-Key is missing");
      }
    };
  }

  @Configuration
  @EnableConfigurationProperties(DslProperties.class)
  static class DslPropertiesConfiguration {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
