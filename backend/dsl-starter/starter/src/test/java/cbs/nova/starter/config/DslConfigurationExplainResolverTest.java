package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.explain.ExplainResourceResolver;
import cbs.nova.dsl.process.TemporalProcessLauncher;
import cbs.nova.dsl.transaction.TransactionInvoker;
import cbs.nova.starter.config.properties.CbsNovaCacheProperties;
import cbs.nova.starter.config.properties.CbsNovaExplainProperties;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.resolver.SpringExplainResourceResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

class DslConfigurationExplainResolverTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withUserConfiguration(DslConfiguration.class, CollaboratorConfiguration.class);

  private ApplicationContextRunner withSourceDir() {
    return runner.withPropertyValues("csb.dsl.source-dir=target/test-dsl");
  }

  @AfterEach
  void clearDslConfigOverrides() {
    var dslConfig = DslConfig.dslConfig();
    dslConfig.explainResourceResolver().replace(null);
    dslConfig.expressionEvaluator().replace(null);
    dslConfig.helperInstanceResolver().replace(null);
    dslConfig.temporalProcessLauncher().replace(null);
    dslConfig.transactionInvoker().replace(null);
    dslConfig.jsonSchemaGenerator().replace(null);
  }

  @Test
  void autoConfigurationProvidesClasspathResolverByDefault() {
    withSourceDir().run(ctx -> {
      assertThat(ctx).hasSingleBean(ExplainResourceResolver.class);
      assertThat(ctx.getBean(ExplainResourceResolver.class))
              .isInstanceOf(SpringExplainResourceResolver.class);
      assertThat(ctx.getBean(ExplainResourceResolver.class).load("batch-processing.md"))
              .contains("#");
    });
  }

  @Test
  void applicationRunnerRegistersResolverOnDslConfig() {
    withSourceDir().run(ctx -> ctx.getBean(ApplicationRunner.class).run(null));

    assertThat(DslConfig.dslConfig().explainResourceResolver().get())
            .isInstanceOf(SpringExplainResourceResolver.class);
  }

  @Test
  void userDefinedResolverBeanWinsOverAutoConfiguration() {
    withSourceDir().withUserConfiguration(UserResolverConfiguration.class)
            .run(ctx -> {
              assertThat(ctx).hasSingleBean(ExplainResourceResolver.class);
              assertThat(ctx.getBean(ExplainResourceResolver.class))
                      .isSameAs(UserResolverConfiguration.USER_RESOLVER);

              ctx.getBean(ApplicationRunner.class).run(null);

              assertThat(DslConfig.dslConfig().explainResourceResolver().get())
                      .isSameAs(UserResolverConfiguration.USER_RESOLVER);
            });
  }

  @Test
  void autoConfiguredResolverHonorsConfiguredPrefix() {
    withSourceDir().withPropertyValues("cbs.nova.explain.resources-prefix=explain/")
            .run(ctx -> assertThat(ctx.getBean(ExplainResourceResolver.class)
                    .load("batch-processing.md"))
                    .contains("#"));
  }

  @Configuration
  @EnableConfigurationProperties({CbsNovaCacheProperties.class, DslProperties.class,
      CbsNovaExplainProperties.class})
  static class CollaboratorConfiguration {

    @Bean
    TemporalProcessLauncher temporalProcessLauncher() {
      return new TemporalProcessLauncher() {
        @Override
        public boolean canRun(Context<?> ctx) {
          return false;
        }

        @Override
        public Result<?> launch(
                String processName,
                String taskQueue,
                Class<?> inputType,
                Class<?> outputType,
                Context<?> ctx) {
          return Result.success("launched");
        }
      };
    }

    @Bean
    TransactionInvoker transactionInvoker() {
      return (_name, _input, _ctx) -> Result.success("invoked");
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }

  @Configuration
  static class UserResolverConfiguration {

    static final ExplainResourceResolver USER_RESOLVER = _path -> "user-resolver-content";

    @Bean
    ExplainResourceResolver userExplainResourceResolver() {
      return USER_RESOLVER;
    }
  }
}
