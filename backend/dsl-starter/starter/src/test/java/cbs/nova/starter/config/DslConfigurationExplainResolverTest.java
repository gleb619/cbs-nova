package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.BeanResolver;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.explain.ExplainResourceResolver;
import cbs.nova.starter.resolver.SpringBeanResolver;
import cbs.nova.dsl.process.TemporalProcessLauncher;
import cbs.nova.dsl.transaction.TransactionInvoker;
import cbs.nova.starter.config.properties.CbsNovaCacheProperties;
import cbs.nova.starter.config.properties.CbsNovaExplainProperties;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.resolver.SpringExplainResourceResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
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
    dslConfig.beanResolver().replace(null);
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
  void explainResolverBeanUsesConfiguredPrefix() {
    var resolver = new DslConfiguration()
            .explainResourceResolver(
                    new CbsNovaExplainProperties(4000, "explain/", 128, 256, 4096));

    assertThat(resolver).isInstanceOf(SpringExplainResourceResolver.class);
    assertThat(resolver.load("batch-processing.md")).contains("#");
  }

  @Test
  void applicationRunnerRegistersResolverOnDslConfig() {
    withSourceDir()
            .run(ctx -> ctx.getBean("dslApplicationRunner", ApplicationRunner.class).run(null));

    assertThat(DslConfig.dslConfig().explainResourceResolver().get())
            .isInstanceOf(SpringExplainResourceResolver.class);
  }

  @Test
  void userDefinedResolverBeanWinsOverAutoConfiguration() {
    new ApplicationContextRunner()
            .withUserConfiguration(UserResolverConfiguration.class, DslConfiguration.class,
                    CollaboratorConfiguration.class)
            .run(ctx -> {
              assertThat(ctx).hasSingleBean(ExplainResourceResolver.class);
              assertThat(ctx.getBean(ExplainResourceResolver.class))
                      .isSameAs(UserResolverConfiguration.USER_RESOLVER);

              ctx.getBean("dslApplicationRunner", ApplicationRunner.class).run(null);

              assertThat(DslConfig.dslConfig().explainResourceResolver().get())
                      .isSameAs(UserResolverConfiguration.USER_RESOLVER);
            });
  }

  @Test
  void applicationRunnerRegistersBeanResolverOnDslConfig() {
    withSourceDir()
            .run(ctx -> ctx.getBean("dslApplicationRunner", ApplicationRunner.class).run(null));

    assertThat(DslConfig.dslConfig().beanResolver().get())
            .isInstanceOf(SpringBeanResolver.class);
  }

  @Test
  void registeredBeanResolverResolvesSpringBeans() {
    withSourceDir().run(ctx -> {
      ctx.getBean("dslApplicationRunner", ApplicationRunner.class).run(null);

      BeanResolver resolver = DslConfig.dslConfig().beanResolver().get();
      assertThat(resolver.resolve(ExplainResourceResolver.class))
              .isInstanceOf(SpringExplainResourceResolver.class);
    });
  }

  @Configuration
  @EnableConfigurationProperties({CbsNovaCacheProperties.class, CbsNovaExplainProperties.class,
      CbsNovaLoggingProperties.class})
  @Import(SpringHelperConfiguration.class)
  static class CollaboratorConfiguration {

    @Bean
    DslProperties dslProperties() {
      return new DslProperties("target/test-dsl", null, null, null, null, null, null, null, null,
              null, null, null);
    }

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
