package cbs.nova.starter.config;

import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.starter.persistence.AesFieldEncryptor;
import cbs.nova.starter.persistence.DslRunJdbcRepository;
import cbs.nova.starter.persistence.DslRunMapper;
import cbs.nova.starter.persistence.DslRunNamingStrategy;
import cbs.nova.starter.persistence.DslRunPersistenceProperties;
import cbs.nova.starter.persistence.FieldEncryptor;
import cbs.nova.starter.persistence.JdbcDslRunRepository;
import cbs.nova.starter.persistence.NoOpFieldEncryptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.relational.core.mapping.NamingStrategy;

import javax.sql.DataSource;

@AutoConfiguration
@EnableConfigurationProperties(DslRunPersistenceProperties.class)
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
          DataSource dataSource,
          DslRunJdbcRepository jdbcRepository,
          DslRunMapper mapper,
          FieldEncryptor encryptor,
          DslRunPersistenceProperties properties) {
    return new JdbcDslRunRepository(dataSource, jdbcRepository, mapper, encryptor, properties);
  }
}
