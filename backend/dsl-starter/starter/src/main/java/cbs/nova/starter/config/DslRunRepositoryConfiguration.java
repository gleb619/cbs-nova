package cbs.nova.starter.config;

import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.starter.config.properties.DslRunPersistenceProperties;
import cbs.nova.starter.converter.DslRunMapper;
import cbs.nova.starter.converter.TransactionExecutionMapper;
import cbs.nova.starter.persistence.AesFieldEncryptor;
import cbs.nova.starter.persistence.DslRunEncryption;
import cbs.nova.starter.persistence.DslRunJdbcRepository;
import cbs.nova.starter.persistence.DslRunNamingStrategy;
import cbs.nova.starter.persistence.ExtendedSelectQueryExecutor;
import cbs.nova.starter.persistence.FieldEncryptor;
import cbs.nova.starter.persistence.JdbcDslRunRepository;
import cbs.nova.starter.persistence.JdbcTransactionExecutionRepository;
import cbs.nova.starter.persistence.NoOpFieldEncryptor;
import cbs.nova.starter.persistence.TransactionExecutionJdbcRepository;
import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.data.relational.core.mapping.NamingStrategy;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

@AutoConfiguration
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
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
  public NamingStrategy dslRunNamingStrategy(DslRunPersistenceProperties properties) {
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
          DslRunPersistenceProperties properties) {
    return new JdbcDslRunRepository(selectQueryExecutor, jdbcRepository, mapper,
            new DslRunEncryption(encryptor), qualifiedTableName(properties));
  }

  @Bean
  @ConditionalOnBean(DataSource.class)
  public ExtendedSelectQueryExecutor extendedSelectQueryExecutor(
          NamedParameterJdbcTemplate jdbcTemplate) {
    return new ExtendedSelectQueryExecutor(jdbcTemplate);
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

  /* ============= */

  // TODO: use `dslRunNamingStrategy` instead
  @Deprecated(forRemoval = true)
  private String qualifiedTableName(DslRunPersistenceProperties properties) {
    String table = properties.tableName() != null && !properties.tableName().isBlank()
            ? properties.tableName()
            : "dsl_runs";
    String schema = properties.schema();
    return (schema != null && !schema.isBlank()) ? schema + "." + table : table;
  }

}
