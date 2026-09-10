package cbs.nova.starter.security;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.RbacFilterConfiguration;
import cbs.nova.starter.config.properties.DslProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies the opt-in contract of {@link RbacFilterConfiguration}: when the
 * {@code cbs.dsl.auth.rbac.enabled} property is absent or false, no RBAC filter beans are
 * registered, mirroring the default-mode behaviour of the pre-T408 starter.
 */
class RbacFilterConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withUserConfiguration(DslPropertiesConfiguration.class,
                  RbacFilterConfiguration.class);

  @Test
  void filterBeansAreAbsentWhenRbacPropertyIsUnset() {
    runner.run(ctx -> {
      assertThat(ctx).doesNotHaveBean(RoleResolver.class);
      assertThat(ctx).doesNotHaveBean(RbacAuthorizationFilter.class);
      assertThat(ctx).doesNotHaveBean(FilterRegistrationBean.class);
    });
  }

  @Test
  void filterBeansAreAbsentWhenRbacPropertyIsFalse() {
    runner.withPropertyValues("cbs.dsl.auth.rbac.enabled=false")
            .run(ctx -> {
              assertThat(ctx).doesNotHaveBean(RoleResolver.class);
              assertThat(ctx).doesNotHaveBean(RbacAuthorizationFilter.class);
              assertThat(ctx).doesNotHaveBean(FilterRegistrationBean.class);
            });
  }

  @Test
  void filterBeansArePresentWhenRbacEnabledTrue() {
    runner.withPropertyValues("cbs.dsl.auth.rbac.enabled=true")
            .run(ctx -> {
              assertThat(ctx).hasSingleBean(RoleResolver.class);
              assertThat(ctx).hasSingleBean(RbacAuthorizationFilter.class);
              assertThat(ctx).hasSingleBean(FilterRegistrationBean.class);

              FilterRegistrationBean<?> reg = ctx.getBean(FilterRegistrationBean.class);
              // Order is HIGHEST_PRECEDENCE + 2 (i.e. -2147483646) so the filter runs AFTER the
              // API-key filter at HIGHEST_PRECEDENCE + 1.
              assertThat(reg.getOrder()).isEqualTo(Integer.MIN_VALUE + 2);
              assertThat(reg.getUrlPatterns()).contains("/api/*");
            });
  }

  @Test
  void configurableClaimNameIsReadFromDslProperties() {
    runner.withPropertyValues(
            "cbs.dsl.auth.rbac.enabled=true",
            "cbs.dsl.auth.rbac.claim=cbs_roles")
            .run(ctx -> {
              assertThat(ctx).hasSingleBean(RoleResolver.class);
              // Construction succeeded with a custom claim; resolver bean is published and filter
              // wiring completes without throwing.
              assertThat(ctx.getBean(RoleResolver.class)).isNotNull();
              assertThat(ctx).hasSingleBean(RbacAuthorizationFilter.class);
            });
  }

  @Test
  void apiKeyAuthDisabledDoesNotPreventRbacFromRegistering() {
    // RBAC is independent of API-key auth. Even when cbs.dsl.auth.enabled is false the RBAC
    // configuration must still come up when its own flag is true — that's exactly the documented
    // "fail-closed" interplay.
    runner.withPropertyValues(
            "cbs.dsl.auth.enabled=false",
            "cbs.dsl.auth.rbac.enabled=true")
            .run(ctx -> {
              assertThat(ctx).hasSingleBean(RoleResolver.class);
              assertThat(ctx).hasSingleBean(RbacAuthorizationFilter.class);
            });
  }

  @Configuration
  @EnableConfigurationProperties(DslProperties.class)
  static class DslPropertiesConfiguration {

    @Bean
    ObjectMapper objectMapper() {
      return JsonMapper.builder().build();
    }
  }
}
