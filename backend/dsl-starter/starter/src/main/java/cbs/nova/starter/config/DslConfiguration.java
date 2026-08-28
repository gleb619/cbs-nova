package cbs.nova.starter.config;

import cbs.nova.dsl.DslDefinitionLoader;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.JsonSchemaGenerator;
import cbs.nova.dsl.ServiceLoaderDslDefinitionLoader;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.helper.HelperInstanceResolver;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.dsl.process.TemporalProcessLauncher;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import cbs.nova.dsl.repository.InMemoryTransactionExecutionRepository;
import cbs.nova.dsl.transaction.TransactionInvoker;
import cbs.nova.dsl.utils.ExpressionEvaluator;
import cbs.nova.dsl.utils.MvelExpressionEvaluator;
import cbs.nova.starter.converter.MapInputConverter;
import cbs.nova.starter.resolver.SpringBeanHelperInstanceResolver;
import cbs.nova.starter.resolver.SpringOrGeneratedHelperInstanceResolver;
import io.avaje.jsonb.Jsonb;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Configuration
public class DslConfiguration {

  @Bean
  public ApplicationRunner dslApplicationRunner(HelperInstanceResolver helperInstanceResolver,
          ExpressionEvaluator expressionEvaluator,
          TransactionInvoker transactionInvoker,
          TemporalProcessLauncher temporalProcessLauncher,
          JsonSchemaGenerator jsonSchemaGenerator,
          DslDefinitionLoader loader) {
    return _ -> {
      GlobalManager.globalManager().resetForTests();
      loadDsl(loader);

      registerExpressionEvaluator(expressionEvaluator);
      registerTemporalProcessLauncher(temporalProcessLauncher);
      registerTransactionInvoker(transactionInvoker);
      registerHelperInstanceResolver(helperInstanceResolver);
      registerHelperResolvers();
      registerJsonSchemaGenerator(jsonSchemaGenerator);
    };
  }

  @Bean
  @ConditionalOnMissingBean(HelperInstanceResolver.class)
  public static HelperInstanceResolver helperInstanceResolver(
          ApplicationContext applicationContext, CbsNovaCacheProperties cacheProperties) {
    List<HelperInstanceResolver> generated = new ArrayList<>();
    ServiceLoader.load(HelperInstanceResolver.class).forEach(generated::add);
    var spec = cacheProperties.specFor(CbsNovaCacheProperties.Names.HELPER_INSTANCE_RESOLUTION);
    return new SpringOrGeneratedHelperInstanceResolver(
            new SpringBeanHelperInstanceResolver(applicationContext),
            generated,
            spec.ttl(),
            spec.maxSize());
  }

  @Bean
  @ConditionalOnMissingBean(DslRunRepository.class)
  public DslRunRepository dslRunRepository() {
    return new InMemoryDslRunRepository();
  }

  @Bean
  @ConditionalOnMissingBean(TransactionExecutionRepository.class)
  public TransactionExecutionRepository transactionExecutionRepository() {
    return new InMemoryTransactionExecutionRepository();
  }

  @Bean
  @ConditionalOnMissingBean(DslDefinitionLoader.class)
  public DslDefinitionLoader dslDefinitionLoader() {
    return new ServiceLoaderDslDefinitionLoader();
  }

  @Bean
  @ConditionalOnMissingBean(ExpressionEvaluator.class)
  public ExpressionEvaluator expressionEvaluator() {
    return new MvelExpressionEvaluator();
  }

  @Bean
  @ConditionalOnMissingBean(MapInputConverter.class)
  public MapInputConverter mapInputConverter(ObjectMapper objectMapper) {
    return new MapInputConverter(Jsonb.builder().build(), objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean(JsonSchemaGenerator.class)
  public JsonSchemaGenerator jsonSchemaGenerator() {
    return DslConfig.dslConfig().jsonSchemaGenerator().get();
  }

  @Bean
  @ConditionalOnMissingBean(HttpClient.class)
  public HttpClient httpClient() {
    return HttpClient.newHttpClient();
  }

  private void loadDsl(DslDefinitionLoader loader) {
    CompletableFuture.supplyAsync(() -> loader.load(GlobalManager.globalManager()))
            .whenComplete((count, ex) -> {
              if (Objects.nonNull(ex)) {
                log.error("DSL_ERROR: ", ex);
              } else {
                log.info("Dsl objects has bean registered: {} objects", count);
              }
            });
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

  private void registerJsonSchemaGenerator(JsonSchemaGenerator jsonSchemaGenerator) {
    DslConfig.dslConfig().jsonSchemaGenerator().replace(jsonSchemaGenerator);
  }

}
