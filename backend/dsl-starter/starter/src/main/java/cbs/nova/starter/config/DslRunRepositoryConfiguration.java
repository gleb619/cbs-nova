package cbs.nova.starter.config;

import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.starter.config.properties.DslRunPersistenceProperties;
import cbs.nova.starter.converter.DslRunMapper;
import cbs.nova.starter.converter.TransactionExecutionMapper;
import cbs.nova.starter.persistence.AesFieldEncryptor;
import cbs.nova.starter.persistence.DslRunJdbcRepository;
import cbs.nova.starter.persistence.DslRunNamingStrategy;
import cbs.nova.starter.persistence.FieldEncryptor;
import cbs.nova.starter.persistence.JdbcDslRunRepository;
import cbs.nova.starter.persistence.JdbcTransactionExecutionRepository;
import cbs.nova.starter.persistence.NoOpFieldEncryptor;
import cbs.nova.starter.persistence.TransactionExecutionJdbcRepository;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.data.relational.core.mapping.NamingStrategy;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;

/**
 * Wires JDBC-backed run repositories and opt-in Flyway schema migrations.
 *
 * <p>
 * Spring Boot 4 does not provide built-in Flyway auto-configuration, so a conditional
 * {@link Flyway} bean plus a {@link org.flywaydb.core.api.callback.Callback}-style initializer is
 * declared here when {@code spring.flyway.enabled=true}.
 *
 * <p>
 * This is a separate auto-configuration (loaded after {@link DataSourceAutoConfiguration}) so
 * {@link DataSource} and the JDBC repositories it depends on are available when this class is
 * processed. The starter's root auto-configuration is ordered after this one so its fallback
 * repository bean backs off in favour of the JDBC implementation.
 */
@AutoConfiguration
@AutoConfigureAfter(org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class)
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
  @ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true")
  @ConditionalOnBean(DataSource.class)
  @ConditionalOnMissingBean(Flyway.class)
  public Flyway flyway(DataSource dataSource) {
    return Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load();
  }

  @Bean
  @ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true")
  @ConditionalOnBean(Flyway.class)
  public org.springframework.boot.ApplicationRunner flywayInitializer(Flyway flyway) {
    return args -> flyway.migrate();
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

  @Bean
  @ConditionalOnBean(DataSource.class)
  @ConditionalOnMissingBean(TransactionExecutionRepository.class)
  public TransactionExecutionRepository transactionExecutionRepository(
          DataSource dataSource,
          TransactionExecutionJdbcRepository jdbcRepository,
          TransactionExecutionMapper mapper,
          ObjectMapper objectMapper) {
    var jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    return new JdbcTransactionExecutionRepository(jdbcTemplate, jdbcRepository, mapper,
            objectMapper);
  }
}
