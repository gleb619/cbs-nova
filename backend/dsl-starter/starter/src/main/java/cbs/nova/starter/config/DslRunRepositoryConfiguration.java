package cbs.nova.starter.config;

import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.starter.config.properties.DslRunPersistenceProperties;
import cbs.nova.starter.events.DomainEventListener;
import cbs.nova.starter.converter.DslAuditMapper;
import cbs.nova.starter.converter.DslRunMapper;
import cbs.nova.starter.converter.TransactionExecutionMapper;
import cbs.nova.starter.persistence.AesFieldEncryptor;
import cbs.nova.starter.persistence.CompileDiagnosticRecordRepository;
import cbs.nova.starter.persistence.DslDefinitionTestRepository;
import cbs.nova.starter.persistence.DslEventRepository;
import cbs.nova.starter.persistence.DslAuditCrudRepository;
import cbs.nova.starter.persistence.DslAuditStore;
import cbs.nova.starter.persistence.DslRunEncryption;
import cbs.nova.starter.persistence.DslRunJdbcRepository;
import cbs.nova.starter.persistence.DslRunNamingStrategy;
import cbs.nova.starter.persistence.ExtendedSelectQueryExecutor;
import cbs.nova.starter.persistence.FieldEncryptor;
import cbs.nova.starter.persistence.JdbcApiKeyRepository;
import cbs.nova.starter.persistence.JdbcDslRunRepository;
import cbs.nova.starter.persistence.JdbcTransactionExecutionRepository;
import cbs.nova.starter.persistence.NoOpFieldEncryptor;
import cbs.nova.starter.persistence.TransactionExecutionJdbcRepository;
import cbs.nova.starter.service.ApiKeyStore;
import cbs.nova.starter.service.DomainEventPublisher;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.service.DslDefinitionTestService;
import cbs.nova.starter.service.DslRuntimeService;
import cbs.nova.starter.webhook.WebhookDeliveryRecordRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;

@EnableConfigurationProperties(DslRunPersistenceProperties.class)
@EnableJdbcRepositories(basePackages = "cbs.nova.starter.persistence")
public class DslRunRepositoryConfiguration {

  @Bean
  @ConditionalOnMissingBean(FieldEncryptor.class)
  @ConditionalOnProperty(name = "cbs.nova.persistence.run.encryption.enabled", havingValue = "true")
  public FieldEncryptor aesFieldEncryptor(DslRunPersistenceProperties properties) {
    return new AesFieldEncryptor(properties.encryptionKey());
  }

  @Bean
  @ConditionalOnMissingBean(FieldEncryptor.class)
  public FieldEncryptor noOpFieldEncryptor() {
    return new NoOpFieldEncryptor();
  }

  @Bean
  @ConditionalOnBean(DataSource.class)
  public DslRunNamingStrategy dslRunNamingStrategy(DslRunPersistenceProperties properties) {
    return new DslRunNamingStrategy(properties);
  }

  @Bean
  @ConditionalOnBean(DataSource.class)
  @ConditionalOnMissingBean(DslRunRepository.class)
  public DslRunRepository dslRunRepository(
          ExtendedSelectQueryExecutor selectQueryExecutor,
          DslRunJdbcRepository jdbcRepository,
          DslRunMapper mapper,
          FieldEncryptor encryptor,
          DslRunNamingStrategy dslRunNamingStrategy) {
    return new JdbcDslRunRepository(selectQueryExecutor, jdbcRepository, mapper,
            new DslRunEncryption(encryptor), dslRunNamingStrategy.qualifiedTableName());
  }

  @Bean
  @ConditionalOnBean(DataSource.class)
  public ExtendedSelectQueryExecutor extendedSelectQueryExecutor(
          NamedParameterJdbcTemplate jdbcTemplate) {
    return new ExtendedSelectQueryExecutor(jdbcTemplate);
  }

  // The Spring Data DslAuditCrudRepository itself is picked up by the @EnableJdbcRepositories
  // scan of cbs.nova.starter.persistence above; its condition is the DataSource, which the
  // repository proxies need at instantiation time. (A @ConditionalOnBean on the repository
  // interface would not match here: registrar-provided bean definitions are registered after
  // this class's own @Bean methods are processed.)
  @Bean
  @ConditionalOnBean(DataSource.class)
  public DslAuditStore dslAuditStore(DslAuditCrudRepository auditRepository,
          DslAuditMapper mapper) {
    return new DslAuditStore(auditRepository, mapper);
  }

  @Bean
  @ConditionalOnBean(DslAuditStore.class)
  public DslAuditService dslAuditService(DslAuditStore auditStore, ObjectMapper objectMapper) {
    return new DslAuditService(auditStore, objectMapper);
  }

  @Bean
  @ConditionalOnBean(DataSource.class)
  public WebhookDeliveryRecordRepository webhookDeliveryRecordRepository(
          NamedParameterJdbcTemplate jdbcTemplate) {
    return new WebhookDeliveryRecordRepository(jdbcTemplate);
  }

  @Bean
  @ConditionalOnBean(DataSource.class)
  public CompileDiagnosticRecordRepository compileDiagnosticRecordRepository(
          NamedParameterJdbcTemplate jdbcTemplate) {
    return new CompileDiagnosticRecordRepository(jdbcTemplate);
  }

  @Bean
  @ConditionalOnBean(DataSource.class)
  public DslDefinitionTestRepository dslDefinitionTestRepository(
          NamedParameterJdbcTemplate jdbcTemplate,
          TransactionTemplate transactionTemplate) {
    return new DslDefinitionTestRepository(jdbcTemplate, transactionTemplate);
  }

  @Bean
  @ConditionalOnBean(DataSource.class)
  public DslDefinitionTestService dslDefinitionTestService(
          DslDefinitionTestRepository repository,
          DslRuntimeService previewService,
          ObjectMapper objectMapper) {
    return new DslDefinitionTestService(repository, previewService, objectMapper);
  }

  @Bean
  @ConditionalOnBean(DataSource.class)
  @ConditionalOnMissingBean(TransactionExecutionRepository.class)
  public TransactionExecutionRepository transactionExecutionRepository(
          TransactionExecutionJdbcRepository jdbcRepository,
          TransactionExecutionMapper mapper,
          ObjectMapper objectMapper) {
    return new JdbcTransactionExecutionRepository(jdbcRepository, mapper,
            objectMapper);
  }

  // --- T411: domain event store -------------------------------------------------
  //
  // The publisher serializes typed DomainEvent records to JSON and inserts one row per
  // event into dsl_events. It is wired through ObjectProvider<DomainEventPublisher> at every
  // transition site so it is optional: in tests / in-memory runs without a DataSource the
  // publisher is absent and the write sites become no-ops rather than failing construction.
  @Bean
  @ConditionalOnBean(DataSource.class)
  public DslEventRepository dslEventRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    return new DslEventRepository(jdbcTemplate);
  }

  @Bean
  @ConditionalOnBean(DslEventRepository.class)
  public DomainEventPublisher domainEventPublisher(DslEventRepository repository,
          ObjectMapper objectMapper, ObjectProvider<DomainEventListener> listeners) {
    return new DomainEventPublisher(repository, objectMapper, listeners);
  }

  // --- T410: rotatable API keys -------------------------------------------------

  @Bean
  @ConditionalOnBean(DataSource.class)
  public JdbcApiKeyRepository jdbcApiKeyRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    return new JdbcApiKeyRepository(jdbcTemplate);
  }

  @Bean
  @ConditionalOnBean(JdbcApiKeyRepository.class)
  public ApiKeyStore apiKeyStore(
          JdbcApiKeyRepository repository,
          ObjectMapper objectMapper,
          ObjectProvider<ApiKeyStore> selfProvider) {
    return new ApiKeyStore(repository, objectMapper, selfProvider);
  }
}
