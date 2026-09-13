package cbs.nova.starter.config;
import cbs.nova.starter.config.properties.CbsNovaCacheProperties;
import cbs.nova.starter.config.properties.CbsNovaExplainProperties;
import cbs.nova.starter.config.properties.DslProperties;

import cbs.nova.dsl.DslDefinitionLoader;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.JsonSchemaGenerator;
import cbs.nova.dsl.DefinitionLoader;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.explain.ExplainResourceResolver;
import cbs.nova.dsl.jsonschema.JacksonJsonSchemaGenerator;
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
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.resolver.SpringBeanHelperInstanceResolver;
import cbs.nova.starter.resolver.SpringExplainResourceResolver;
import cbs.nova.starter.service.DefaultDslWorkspaceResolver;
import cbs.nova.starter.service.DslFileBulkhead;
import cbs.nova.starter.service.DslWorkspaceResolver;
import cbs.nova.starter.resolver.SpringOrGeneratedHelperInstanceResolver;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.avaje.jsonb.Jsonb;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Configuration
public class DslConfiguration {

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public ApplicationRunner dslApplicationRunner(HelperInstanceResolver helperInstanceResolver,
          ExpressionEvaluator expressionEvaluator,
          TransactionInvoker transactionInvoker,
          TemporalProcessLauncher temporalProcessLauncher,
          JsonSchemaGenerator jsonSchemaGenerator,
          ExplainResourceResolver explainResourceResolver,
          DslDefinitionLoader loader) {
    return _ -> {
      GlobalManager.globalManager().resetForTests();

      initRegistry();
      loadDsl(loader);

      registerExpressionEvaluator(expressionEvaluator);
      registerTemporalProcessLauncher(temporalProcessLauncher);
      registerTransactionInvoker(transactionInvoker);
      registerHelperInstanceResolver(helperInstanceResolver);
      registerHelperResolvers();
      registerJsonSchemaGenerator(jsonSchemaGenerator);
      registerExplainResourceResolver(explainResourceResolver);
    };
  }

  @Bean
  @ConditionalOnMissingBean(HelperInstanceResolver.class)
  public static HelperInstanceResolver helperInstanceResolver(
          ApplicationContext applicationContext, CbsNovaCacheProperties cacheProperties) {
    List<HelperInstanceResolver> generated = new ArrayList<>();
    ServiceLoader.load(HelperInstanceResolver.class).forEach(generated::add);
    var spec = cacheProperties.specFor(StarterConstants.HELPER_INSTANCE_RESOLUTION);
    Cache<Class<?>, Executable<?, ?>> cache = Caffeine.newBuilder()
            .expireAfterWrite(spec.ttl())
            .maximumSize(spec.maxSize())
            .build();
    return new SpringOrGeneratedHelperInstanceResolver(
            new SpringBeanHelperInstanceResolver(applicationContext),
            generated,
            cache);
  }


  public static SpringOrGeneratedHelperInstanceResolver withDefaultCache(
          HelperInstanceResolver springResolver,
          List<HelperInstanceResolver> generatedFactories) {
    Cache<Class<?>, Executable<?, ?>> cache = Caffeine.newBuilder()
            .expireAfterWrite(StarterConstants.HELPER_INSTANCE_CACHE_TTL)
            .maximumSize(StarterConstants.HELPER_INSTANCE_CACHE_MAX_SIZE)
            .build();
    return new SpringOrGeneratedHelperInstanceResolver(springResolver, generatedFactories, cache);
  }

  @Bean
  @ConditionalOnMissingBean(DslRunRepository.class)
  @ConditionalOnMissingClass("cbs.nova.starter.persistence.JdbcDslRunRepository")
  public DslRunRepository dslRunRepository(
          TransactionExecutionRepository transactionExecutionRepository) {
    // Cascade eviction only onto the in-memory sibling: a persistent repository must not lose
    // rows just because the in-memory run history rolled over its capacity bound.
    if (transactionExecutionRepository instanceof InMemoryTransactionExecutionRepository inMemory) {
      return new InMemoryDslRunRepository(inMemory::deleteByRunId);
    }
    return new InMemoryDslRunRepository(InMemoryDslRunRepository.NO_OP_EVICTION);
  }

  @Bean
  @ConditionalOnMissingBean(TransactionExecutionRepository.class)
  @ConditionalOnMissingClass("cbs.nova.starter.persistence.JdbcTransactionExecutionRepository")
  public TransactionExecutionRepository transactionExecutionRepository() {
    return new InMemoryTransactionExecutionRepository();
  }

  @Bean
  @ConditionalOnMissingBean(DslDefinitionLoader.class)
  public DslDefinitionLoader dslDefinitionLoader() {
    return new DefinitionLoader();
  }

  @Bean
  @ConditionalOnMissingBean(ExpressionEvaluator.class)
  public ExpressionEvaluator expressionEvaluator() {
    return new MvelExpressionEvaluator();
  }

  @Bean
  @ConditionalOnMissingBean(ExplainResourceResolver.class)
  public ExplainResourceResolver explainResourceResolver(
          CbsNovaExplainProperties explainProperties) {
    return new SpringExplainResourceResolver(explainProperties.resourcesPrefix());
  }

  @Bean
  @ConditionalOnMissingBean(MapInputConverter.class)
  public MapInputConverter mapInputConverter(ObjectMapper objectMapper) {
    return new MapInputConverter(Jsonb.builder().build(), objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean(JsonSchemaGenerator.class)
  public JsonSchemaGenerator jsonSchemaGenerator() {
    return new JacksonJsonSchemaGenerator();
  }

  @Bean
  @ConditionalOnMissingBean(DslWorkspaceResolver.class)
  public DslWorkspaceResolver dslWorkspaceResolver(DslProperties dslProperties) {
    String sourceDir = dslProperties.sourceDir();
    if (sourceDir == null || sourceDir.isBlank()) {
      throw new IllegalStateException("csb.dsl.source-dir is not configured");
    }

    var sourceRoot = Path.of(sourceDir).normalize();
    var configured = Path.of(dslProperties.workbenchWorkspaceRoot());
    var workspaceRoot = (configured.isAbsolute() ? configured : sourceRoot.resolve(configured))
            .normalize();
    return new DefaultDslWorkspaceResolver(sourceRoot, workspaceRoot);
  }

  @Bean
  @ConditionalOnMissingBean(HttpClient.class)
  public HttpClient httpClient() {
    return HttpClient.newHttpClient();
  }

  @Bean
  public DslFileBulkhead dslFileBulkhead(DslProperties properties) {
    int readPermits = properties.files().readBulkheadPermits();
    int writePermits = properties.files().writeBulkheadPermits();
    var readSemaphore = new Semaphore(Math.max(1, readPermits));
    var writeSemaphore = new Semaphore(Math.max(1, writePermits));

    return new DslFileBulkhead(readSemaphore, writeSemaphore,
            properties.files().acquireTimeoutSeconds());
  }

  private void initRegistry() {
    DslConfig.dslConfig().generatedClassRegistry()
            .init(GlobalManager.globalManager().defaultClassLoader());
  }

  private void loadDsl(DslDefinitionLoader loader) {
    CompletableFuture.supplyAsync(() -> loader.load(GlobalManager.globalManager()))
            .whenComplete((result, ex) -> {
              if (Objects.nonNull(ex)) {
                log.error("DSL_ERROR: ", ex);
              } else if (result != null) {
                log.info("Dsl objects registered: {} objects (processes={}, transactions={},"
                        + " functions={})",
                        result.total(), result.processCount(), result.transactionCount(),
                        result.functionCount());
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

  private void registerExplainResourceResolver(ExplainResourceResolver explainResourceResolver) {
    DslConfig.dslConfig().explainResourceResolver().replace(explainResourceResolver);
  }

}
