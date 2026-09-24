package cbs.nova.starter;

import cbs.nova.starter.converter.DslAuditMapperImpl;
import cbs.nova.starter.model.DslAudit;
import cbs.nova.starter.persistence.DslAuditCrudRepository;
import cbs.nova.starter.persistence.DslAuditStore;
import cbs.nova.starter.service.DslAuditService;
import java.util.UUID;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

public final class AuditTestSupport {

  private AuditTestSupport() {
  }

  public record Harness(DslAuditService service, DslAuditStore store) {

    public void append(DslAudit row) {
      store.append(row);
    }
  }

  public static Harness h2() {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:audit-" + UUID.randomUUID().toString().replace("-", "")
            + ";DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
    dataSource.setUser("sa");
    try {
      ScriptUtils.executeSqlScript(dataSource.getConnection(),
              new ClassPathResource("db/migration/h2/V1__init.sql"));
    } catch (Exception e) {
      throw new IllegalStateException("failed to set up in-memory dsl_audit table", e);
    }
    var context = new AnnotationConfigApplicationContext();
    context.registerBean("auditDataSource", DataSource.class, () -> dataSource);
    context.registerBean("transactionManager", PlatformTransactionManager.class,
            () -> new DataSourceTransactionManager(dataSource));
    context.registerBean(NamedParameterJdbcOperations.class,
            () -> new NamedParameterJdbcTemplate(dataSource));
    context.register(AuditRepositoryConfig.class);
    context.refresh();
    var store = new DslAuditStore(context.getBean(DslAuditCrudRepository.class),
            new DslAuditMapperImpl());
    return new Harness(new DslAuditService(store, new ObjectMapper()), store);
  }

  /**
   * Registers the Spring Data proxy for {@link DslAuditCrudRepository} over the in-memory H2
   * {@code dsl_audit} table, mirroring the {@code @EnableJdbcRepositories} scan of
   * {@code DslRunRepositoryConfiguration}.
   */
  @Configuration
  @EnableJdbcRepositories(basePackageClasses = DslAuditCrudRepository.class, considerNestedRepositories = false, includeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = DslAuditCrudRepository.class))
  static class AuditRepositoryConfig extends AbstractJdbcConfiguration {
  }

  public static ObjectProvider<DslAuditService> providerOf(DslAuditService service) {
    return new ObjectProvider<>() {
      @Override
      public DslAuditService getObject() {
        return service;
      }

      @Override
      public DslAuditService getIfAvailable() {
        return service;
      }

      @Override
      public DslAuditService getIfUnique() {
        return service;
      }
    };
  }

  public static ObjectProvider<DslAuditService> emptyProvider() {
    return new ObjectProvider<>() {
      @Override
      public DslAuditService getObject() {
        return null;
      }

      @Override
      public DslAuditService getIfAvailable() {
        return null;
      }

      @Override
      public DslAuditService getIfUnique() {
        return null;
      }
    };
  }
}
