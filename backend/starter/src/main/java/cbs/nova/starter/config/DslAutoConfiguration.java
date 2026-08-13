package cbs.nova.starter.config;

import cbs.nova.dsl.DefinitionLoader;
import cbs.nova.dsl.DslRunRepository;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.HelperInstanceResolver;
import cbs.nova.dsl.TemporalProcessLauncher;
import cbs.nova.dsl.TransactionInvoker;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import cbs.nova.dsl.utils.ExpressionEvaluator;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.expression.MvelExpressionEvaluator;
import cbs.nova.starter.resolver.SpringBeanHelperInstanceResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(DslProperties.class)
public class DslAutoConfiguration {

  @Bean
  @ConditionalOnProperty(name = "dsl.source-dir")
  public ApplicationRunner dslApplicationRunner(HelperInstanceResolver helperInstanceResolver,
          ExpressionEvaluator expressionEvaluator,
          TransactionInvoker transactionInvoker,
          TemporalProcessLauncher temporalProcessLauncher,
          DslProperties dslProperties) {
    return _ -> {
      var dir = acquireSourceDir(dslProperties.sourceDir());
      new DefinitionLoader().load(dir, GlobalManager.globalManager());

      registerHelperResolvers();
      registerExpressionEvaluator(expressionEvaluator);
      registerTemporalProcessLauncher(temporalProcessLauncher);
      registerTransactionInvoker(transactionInvoker);
      registerHelperInstanceResolver(helperInstanceResolver);
    };
  }

  @Bean
  @ConditionalOnMissingBean(HelperInstanceResolver.class)
  public static HelperInstanceResolver helperInstanceResolver(
          ApplicationContext applicationContext) {
    return new SpringBeanHelperInstanceResolver(applicationContext);
  }

  @Bean
  @ConditionalOnMissingBean(DslRunRepository.class)
  public DslRunRepository dslRunRepository() {
    return new InMemoryDslRunRepository();
  }

  @Bean
  @ConditionalOnMissingBean(ExpressionEvaluator.class)
  public ExpressionEvaluator expressionEvaluator() {
    return new MvelExpressionEvaluator();
  }

  private void registerExpressionEvaluator(ExpressionEvaluator expressionEvaluator) {
    DslConfig.dslConfig().expressionEvaluator().replace(expressionEvaluator);
  }

  private void registerHelperInstanceResolver(HelperInstanceResolver helperInstanceResolver) {
    DslConfig.dslConfig().helperInstanceResolver()
            .replace(helperInstanceResolver);
  }

  private void registerHelperResolvers() {
    GlobalManager.globalManager().registerHelperResolvers();
  }

  private void registerTemporalProcessLauncher(TemporalProcessLauncher temporalProcessLauncher) {
    DslConfig.dslConfig().temporalProcessLauncher()
            .replace(temporalProcessLauncher);
  }

  private void registerTransactionInvoker(TransactionInvoker transactionInvoker) {
    DslConfig.dslConfig().transactionInvoker()
            .replace(transactionInvoker);
  }

  private Path acquireSourceDir(String sourceDirProperty) {
    if (sourceDirProperty == null || sourceDirProperty.isBlank()) {
      throw new NullPointerException(
              "dsl.source-dir can't be empty");
    }

    var dir = Path.of(sourceDirProperty);
    if (!Files.isDirectory(dir)) {
      throw new IllegalStateException(
              "dsl.source-dir does not exist or is not a directory: " + dir);
    }

    return dir;
  }

}
