package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.properties.CbsNovaCacheProperties;
import cbs.nova.starter.config.properties.DslBuilderClientProperties;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.config.router.DslDraftRouterConfiguration;
import cbs.nova.starter.controller.BuilderApiErrorHandler;
import cbs.nova.starter.controller.DslDraftHandler;
import cbs.nova.starter.controller.DslReloadHandler;
import cbs.nova.starter.service.DslDefinitionBundleService;
import cbs.nova.starter.service.DslDefinitionHistoryService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.function.RouterFunction;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies that the canonical {@code cbs.dsl.*} property prefix is used everywhere and that the
 * conditional beans are wired based on {@code cbs.dsl.*} keys.
 */
class CbsDslPropertyPrefixTest {

  private static ApplicationContextRunner runner() {
    return new ApplicationContextRunner();
  }

  @Test
  void draftsHandlerPresentByDefault() {
    runner()
            .withUserConfiguration(DraftsTestConfig.class, DslDraftHandler.class)
            .run(ctx -> assertThat(ctx).hasSingleBean(DslDraftHandler.class));
  }

  @Test
  void draftsHandlerAbsentWhenCbsDisabled() {
    runner()
            .withUserConfiguration(DraftsTestConfig.class, DslDraftHandler.class)
            .withPropertyValues("cbs.dsl.drafts.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(DslDraftHandler.class));
  }

  @Test
  void draftsRouterIsAbsentWhenCbsDisabled() {
    runner()
            .withUserConfiguration(DraftsTestConfig.class,
                    DslDraftRouterConfiguration.class, DslDraftHandler.class)
            .withPropertyValues("cbs.dsl.drafts.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(RouterFunction.class));
  }

  @Test
  void builderClientPropertiesBoundFromCbs() {
    runner()
            .withUserConfiguration(BuilderClientPropertiesConfig.class)
            .withPropertyValues("cbs.dsl.builder-client.base-url=http://builder:8080")
            .run(ctx -> {
              DslBuilderClientProperties properties = ctx.getBean(DslBuilderClientProperties.class);
              assertThat(properties.baseUrl()).isEqualTo("http://builder:8080");
            });
  }

  @Configuration
  @EnableConfigurationProperties(DslProperties.class)
  static class DraftsTestConfig {

    @Bean
    DslReloadHandler dslReloadHandler(DslProperties props) {
      return new DslReloadHandler(props, null, null, null, null, null, null, null);
    }

    @Bean
    DslDefinitionHistoryService dslDefinitionHistoryService(DslProperties props,
            ObjectMapper mapper) {
      return new DslDefinitionHistoryService(props, mapper);
    }

    @Bean
    DslDefinitionBundleService dslDefinitionBundleService(ObjectMapper mapper) {
      return new DslDefinitionBundleService(mapper, Optional.empty(),
              DslProperties.bundleServiceDefaults());
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }

  @Configuration
  @EnableConfigurationProperties(DslBuilderClientProperties.class)
  static class BuilderClientPropertiesConfig {
  }

  @Configuration
  static class BuilderClientInfrastructureConfig {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    CbsNovaCacheProperties cbsNovaCacheProperties() {
      return new CbsNovaCacheProperties(null, null);
    }

    @Bean
    BuilderApiErrorHandler builderApiErrorHandler(ObjectMapper mapper) {
      return new BuilderApiErrorHandler(mapper);
    }

    @Bean
    RestClient.Builder restClientBuilder() {
      return RestClient.builder();
    }
  }
}
